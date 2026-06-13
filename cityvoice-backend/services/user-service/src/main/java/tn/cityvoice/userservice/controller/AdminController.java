package tn.cityvoice.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.userservice.entity.InvitationCode;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.repository.InvitationCodeRepository;
import tn.cityvoice.userservice.repository.UserRepository;
import tn.cityvoice.userservice.service.AnomalyService;
import tn.cityvoice.userservice.service.ChurnService;
import tn.cityvoice.userservice.service.SegmentationService;
import tn.cityvoice.userservice.service.UserService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    final InvitationCodeRepository invitationCodeRepository;
    final UserRepository userRepository;
    final UserService userService;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    private final RestTemplate restTemplate;

    // Injecter ChurnService
    final ChurnService churnService;

    // segmentation + anomaly
    private final SegmentationService segmentationService;
    private final AnomalyService anomalyService;

    // ── Générer un code ───────────────────────────────────
    @PostMapping("/invitation-codes")
    public ResponseEntity<?> generateCode(@RequestBody Map<String, String> body) {
        String roleStr = body.get("role");

        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Rôle invalide");
        }

        if (role == Role.CITOYEN) {
            return ResponseEntity.badRequest().body("Réservé aux agents");
        }

        String code = UUID.randomUUID()
                .toString().replace("-", "")
                .substring(0, 8).toUpperCase();

        InvitationCode invitation = new InvitationCode();
        invitation.setCode(code);
        invitation.setRole(role);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        invitationCodeRepository.save(invitation);

        return ResponseEntity.ok(invitation);
    }

    // ── Lister avec pagination + filtres ──────────────────
    @GetMapping("/invitation-codes")
    public ResponseEntity<?> getAllCodes(
            @RequestParam(name = "page",   defaultValue = "0")  int    page,
            @RequestParam(name = "size",   defaultValue = "10") int    size,
            @RequestParam(name = "role",   required = false)    String role,
            @RequestParam(name = "status", required = false)    String status) {

        List<InvitationCode> all = invitationCodeRepository.findAll();

        // Log pour débugger
        all.forEach(c -> {
            System.out.println("Code: " + c.getCode()
                    + " | used: " + c.isUsed()
                    + " | usedByUser: " + (c.getUsedByUser() != null ? c.getUsedByUser().getNom() : "NULL"));
        });

        // ── Filtrer par rôle ──────────────────────────
        if (role != null && !role.isBlank()) {
            try {
                Role r = Role.valueOf(role);
                all = all.stream()
                        .filter(c -> c.getRole() == r)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ignored) {}
        }

        // ── Filtrer par statut ────────────────────────
        if (status != null && !status.isBlank()) {
            LocalDateTime now = LocalDateTime.now();
            all = all.stream().filter(c -> switch (status) {
                case "active"  -> !c.isUsed() && now.isBefore(c.getExpiresAt());
                case "used"    ->  c.isUsed();
                case "expired" -> !c.isUsed() && now.isAfter(c.getExpiresAt());
                default        -> true;
            }).collect(Collectors.toList());
        }

        // ── Trier : actifs en premier, puis par date desc ─
        all.sort(Comparator
                .comparing(InvitationCode::isUsed)
                .thenComparing(Comparator.comparing(InvitationCode::getExpiresAt).reversed()));

        // ── Paginer manuellement ──────────────────────
        int total = all.size();
        int start = page * size;
        int end   = Math.min(start + size, total);

        List<InvitationCode> pageContent = (start >= total)
                ? List.of()
                : all.subList(start, end);

        return ResponseEntity.ok(Map.of(
                "content",       pageContent,
                "totalElements", total,
                "totalPages",    (int) Math.ceil((double) total / size),
                "currentPage",   page
        ));
    }

    // ── Révoquer ──────────────────────────────────────────
    @PatchMapping("/invitation-codes/{code}/revoke")
    public ResponseEntity<?> revokeCode(@PathVariable("code") String code) {
        return invitationCodeRepository.findByCode(code)
                .map(c -> {
                    if (c.isUsed()) {
                        String usedBy = c.getUsedByUser() != null
                                ? c.getUsedByUser().getNom()
                                : "un agent";
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Code déjà utilisé par " + usedBy));
                    }
                    c.setUsed(true);
                    invitationCodeRepository.save(c);
                    return ResponseEntity.ok(Map.of("message", "Code révoqué"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Supprimer ─────────────────────────────────────────
    @DeleteMapping("/invitation-codes/{id}")
    public ResponseEntity<?> deleteCode(@PathVariable("id") UUID id) {
        return invitationCodeRepository.findById(id)
                .map(c -> {
                    if (c.isUsed() && c.getUsedByUser() != null) {
                        return ResponseEntity.status(409).body(Map.of(
                                "error",   "Impossible de supprimer",
                                "message", "Ce code a été utilisé par "
                                        + c.getUsedByUser().getNom()
                                        + " (" + c.getUsedByUser().getEmail() + ")"
                        ));
                    }
                    invitationCodeRepository.deleteById(id);
                    return ResponseEntity.ok(Map.of("message", "Code supprimé"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // analytics :
    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics() {
        List<User> allUsers = userRepository.findAll();
        LocalDateTime now   = LocalDateTime.now();

        // ── Inscriptions par jour (7 derniers jours) ──────────
        Map<String, Long> inscriptionsParJour = new java.util.LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime day = now.minusDays(i).toLocalDate().atStartOfDay();
            LocalDateTime end = day.plusDays(1);
            String label = day.toLocalDate().toString();
            long count = allUsers.stream()
                    .filter(u -> u.getDateInscription() != null
                            && u.getDateInscription().isAfter(day)
                            && u.getDateInscription().isBefore(end))
                    .count();
            inscriptionsParJour.put(label, count);
        }

        // ── Répartition par rôle ──────────────────────────────
        Map<String, Long> parRole = allUsers.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        u -> u.getRole().name(),
                        java.util.stream.Collectors.counting()
                ));

        // ── Répartition par gouvernorat (top 8) ───────────────
        Map<String, Long> parGouvernorat = allUsers.stream()
                .filter(u -> u.getGouvernorat() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        User::getGouvernorat,
                        java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, java.util.LinkedHashMap::new
                ));

        // ── Répartition par trust level (citoyens seulement) ──
        Map<String, Long> parTrustLevel = allUsers.stream()
                .filter(u -> u.getRole() == Role.CITOYEN
                        && u.getTrustLevel() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        u -> u.getTrustLevel().name(),
                        java.util.stream.Collectors.counting()
                ));

        // ── Répartition par statut ────────────────────────────
        Map<String, Long> parStatut = new java.util.LinkedHashMap<>();
        parStatut.put("ACTIF",      allUsers.stream().filter(u -> "ACTIF".equals(userService.getStatut(u))).count());
        parStatut.put("NOUVEAU",    allUsers.stream().filter(u -> "NOUVEAU".equals(userService.getStatut(u))).count());
        parStatut.put("INCOMPLET",  allUsers.stream().filter(u -> "INCOMPLET".equals(userService.getStatut(u))).count());
        parStatut.put("EN_ATTENTE", allUsers.stream().filter(u -> "EN_ATTENTE".equals(userService.getStatut(u))).count());
        parStatut.put("SUSPENDU",   allUsers.stream().filter(u -> "SUSPENDU".equals(userService.getStatut(u))).count());

        // ── KPIs globaux ──────────────────────────────────────
        long totalUsers      = allUsers.size();
        long totalCitoyens   = allUsers.stream().filter(u -> u.getRole() == Role.CITOYEN).count();
        long totalAgents     = allUsers.stream().filter(u -> u.getRole() != Role.CITOYEN && u.getRole() != Role.ADMIN_VILLE).count();
        long totalBanned     = allUsers.stream().filter(User::isBanned).count();
        long emailVerified   = allUsers.stream().filter(User::isEmailVerified).count();
        long newThisWeek     = allUsers.stream()
                .filter(u -> u.getDateInscription() != null
                        && u.getDateInscription().isAfter(now.minusDays(7)))
                .count();
        long newThisMonth    = allUsers.stream()
                .filter(u -> u.getDateInscription() != null
                        && u.getDateInscription().isAfter(now.withDayOfMonth(1).withHour(0).withMinute(0)))
                .count();
        double avgPoints     = allUsers.stream()
                .filter(u -> u.getRole() == Role.CITOYEN)
                .mapToInt(User::getPoints)
                .average().orElse(0);
        double verifiedRate  = totalUsers > 0
                ? Math.round((double) emailVerified / totalUsers * 100.0) : 0;

        return ResponseEntity.ok(Map.of(
                "kpis", Map.of(
                        "totalUsers",    totalUsers,
                        "totalCitoyens", totalCitoyens,
                        "totalAgents",   totalAgents,
                        "totalBanned",   totalBanned,
                        "newThisWeek",   newThisWeek,
                        "newThisMonth",  newThisMonth,
                        "avgPoints",     Math.round(avgPoints),
                        "verifiedRate",  verifiedRate
                ),
                "inscriptionsParJour", inscriptionsParJour,
                "parRole",             parRole,
                "parGouvernorat",      parGouvernorat,
                "parTrustLevel",       parTrustLevel,
                "parStatut",           parStatut
        ));
    }

    // ai analytics :
    @GetMapping("/users/{id}/behavior-analysis")
    public ResponseEntity<?> analyzeBehavior(@PathVariable("id") UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (groqApiKey == null || groqApiKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "AI non configurée"));
        }

        boolean profileComplete = user.getNom() != null
                && user.getTelephone() != null
                && user.getGouvernorat() != null
                && user.getVille() != null
                && user.getPhoto() != null;

        long daysSinceInscription = user.getDateInscription() != null
                ? java.time.temporal.ChronoUnit.DAYS.between(
                user.getDateInscription().toLocalDate(), java.time.LocalDate.now())
                : 0;

        String prompt = """
        Tu es un analyste IA pour CityVoice, plateforme civique tunisienne.
        Analyse ce profil et génère un rapport comportemental.
        
        Données utilisateur :
        - Nom : %s
        - Rôle : %s
        - Points : %d
        - Trust level : %s
        - Email vérifié : %b
        - Login streak : %d jours
        - Membre depuis : %d jours
        - Banni : %b
        - Photo présente : %b
        - Profil complet : %b
        
        Réponds UNIQUEMENT avec un JSON valide, sans markdown, sans explication :
        {
          "persona": "nom court du type de citoyen (ex: Sentinelle du quartier)",
          "engagement": "FAIBLE ou MODERE ou ELEVE ou TRES_ELEVE",
          "risque": "FAIBLE ou MOYEN ou ELEVE",
          "points_forts": ["point 1", "point 2"],
          "points_attention": ["attention 1"],
          "recommandation": "une phrase courte d'action pour l'administrateur"
        }
        """.formatted(
                user.getNom(),
                user.getRole().name(),
                user.getPoints(),
                user.getTrustLevel().name(),
                user.isEmailVerified(),
                user.getLoginStreak(),
                daysSinceInscription,
                user.isBanned(),
                user.getPhoto() != null,
                profileComplete
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            Map<String, Object> requestBody = Map.of(
                    "model",       "llama-3.1-8b-instant",
                    "max_tokens",  300,
                    "temperature", 0.3,
                    "messages", List.of(
                            Map.of(
                                    "role",    "user",
                                    "content", prompt
                            )
                    )
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    groqApiUrl, HttpMethod.POST, request, Map.class
            );

            if (response.getBody() == null) {
                return ResponseEntity.status(502).body(Map.of("error", "Réponse vide"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");

            String text = "";
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message =
                        (Map<String, Object>) choices.get(0).get("message");
                if (message != null) text = (String) message.get("content");
            }

            if (text == null || text.isBlank()) {
                return ResponseEntity.status(502).body(Map.of("error", "Réponse vide"));
            }

            // Strip markdown code fences if Groq wraps the JSON
            text = text.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*",     "")
                    .replaceAll("```$",         "")
                    .trim();

            return ResponseEntity.ok(Map.of("analysis", text));

        } catch (Exception e) {
            System.out.println("[BEHAVIOR AI] Exception: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "AI indisponible"));
        }
    }

    // churn
    @GetMapping("/users/{id}/churn")
    public ResponseEntity<?> getChurnPrediction(@PathVariable("id") UUID id) {
        // Seulement pour les citoyens
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() != Role.CITOYEN) {
            return ResponseEntity.ok(Map.of("message", "Non applicable aux agents"));
        }
        return ResponseEntity.ok(churnService.predict(id));
    }

    @GetMapping("/churn/high-risk")
    public ResponseEntity<?> getHighRiskUsers() {
        List<User> citoyens = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CITOYEN && !u.isBanned())
                .toList();

        List<Map<String, Object>> highRisk = citoyens.stream()
                .map(u -> {
                    ChurnService.ChurnPrediction pred = churnService.predict(u.getId());
                    if (pred.churnProbability() < 0.5) return null;
                    Map<String, Object> r = new java.util.LinkedHashMap<>();
                    r.put("userId",            u.getId());
                    r.put("nom",               u.getNom());
                    r.put("email",             u.getEmail());
                    r.put("photo",             u.getPhoto());
                    r.put("churnProbability",  pred.churnProbability());
                    r.put("riskLevel",         pred.riskLevel());
                    r.put("daysUntilChurn",    pred.daysUntilChurn());
                    r.put("topRiskFactor",     pred.riskFactors().isEmpty()
                            ? null : pred.riskFactors().get(0));
                    return r;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Double.compare(
                        (Double) b.get("churnProbability"),
                        (Double) a.get("churnProbability")
                ))
                .limit(20)
                .toList();

        return ResponseEntity.ok(Map.of(
                "users",      highRisk,
                "total",      highRisk.size(),
                "critical",   highRisk.stream().filter(u -> "CRITICAL".equals(u.get("riskLevel"))).count(),
                "high",       highRisk.stream().filter(u -> "HIGH".equals(u.get("riskLevel"))).count()
        ));
    }

    // ══════════════════════════════════════════════════════════════
// SEGMENTATION
// ══════════════════════════════════════════════════════════════

    @GetMapping("/users/{id}/segment")
    public ResponseEntity<?> getUserSegment(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(segmentationService.segment(id));
    }

// ══════════════════════════════════════════════════════════════
// ANOMALY DETECTION
// ══════════════════════════════════════════════════════════════

    @GetMapping("/users/{id}/anomaly")
    public ResponseEntity<?> getUserAnomaly(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(anomalyService.detect(id));
    }

// ══════════════════════════════════════════════════════════════
// COMPLETE ML ANALYSIS (all models at once)
// ══════════════════════════════════════════════════════════════

    @GetMapping("/users/{id}/ml-analysis")
    public ResponseEntity<?> getCompleteMLAnalysis(@PathVariable("id") UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("userId", id.toString());
        result.put("nom", user.getNom());

        // Churn
        if (user.getRole() == Role.CITOYEN) {
            result.put("churn", churnService.predict(id));
        }

        // Segmentation
        result.put("segmentation", segmentationService.segment(id));

        // Anomaly
        result.put("anomaly", anomalyService.detect(id));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/insights")
    public ResponseEntity<?> getAiInsights() {
        List<User> allUsers = userRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        // Build context for the AI
        long totalUsers    = allUsers.size();
        long totalCitoyens = allUsers.stream().filter(u -> u.getRole() == Role.CITOYEN).count();
        long totalAgents   = allUsers.stream().filter(u -> u.getRole() != Role.CITOYEN && u.getRole() != Role.ADMIN_VILLE).count();
        long totalBanned   = allUsers.stream().filter(User::isBanned).count();
        long notVerified   = allUsers.stream().filter(u -> !u.isEmailVerified()).count();

        long newThisWeek = allUsers.stream()
                .filter(u -> u.getDateInscription() != null
                        && u.getDateInscription().isAfter(now.minusDays(7)))
                .count();

        long newLastWeek = allUsers.stream()
                .filter(u -> u.getDateInscription() != null
                        && u.getDateInscription().isAfter(now.minusDays(14))
                        && u.getDateInscription().isBefore(now.minusDays(7)))
                .count();

        double growthPct = newLastWeek > 0
                ? Math.round(((double)(newThisWeek - newLastWeek) / newLastWeek) * 100.0)
                : 0;

        double verifiedRate = totalUsers > 0
                ? Math.round((double)(totalUsers - notVerified) / totalUsers * 100.0)
                : 0;

        double avgPoints = allUsers.stream()
                .filter(u -> u.getRole() == Role.CITOYEN)
                .mapToInt(User::getPoints)
                .average().orElse(0);

        // Count users with high churn risk
        long highRiskCount = 0;
        try {
            highRiskCount = allUsers.stream()
                    .filter(u -> u.getRole() == Role.CITOYEN && !u.isBanned())
                    .filter(u -> {
                        try {
                            var pred = churnService.predict(u.getId());
                            return pred.churnProbability() >= 0.6;
                        } catch (Exception e) { return false; }
                    })
                    .count();
        } catch (Exception e) {
            System.out.println("[INSIGHTS] Churn analysis skipped: " + e.getMessage());
        }

        // Count ambassadeur-level users (trust level AMBASSADEUR)
        long ambassadeurCount = allUsers.stream()
                .filter(u -> u.getRole() == Role.CITOYEN
                        && u.getTrustLevel() != null
                        && u.getTrustLevel().name().equals("AMBASSADEUR"))
                .count();

        // Count incomplete profiles
        long incompleteProfiles = allUsers.stream()
                .filter(u -> "INCOMPLET".equals(userService.getStatut(u)))
                .count();

        // Average login streak
        double avgStreak = allUsers.stream()
                .filter(u -> u.getRole() == Role.CITOYEN)
                .mapToInt(User::getLoginStreak)
                .average().orElse(0);

        if (groqApiKey == null || groqApiKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "AI non configurée"));
        }

        String prompt = """
        Tu es un analyste IA expert en plateformes civiques pour CityVoice (Tunisie).
        Analyse ces métriques et génère des insights actionnables pour l'administrateur.

        Métriques plateforme :
        - Total utilisateurs : %d
        - Citoyens actifs : %d
        - Agents municipaux : %d
        - Comptes suspendus : %d
        - Emails non vérifiés : %d (taux vérification: %.0f%%)
        - Profils incomplets : %d
        - Nouveaux inscrits cette semaine : %d (vs %d semaine précédente, croissance: %.0f%%)
        - Points moyens par citoyen : %.0f
        - Streak de connexion moyen : %.1f jours
        - Citoyens à risque de départ élevé : %d
        - Citoyens niveau Ambassadeur : %d

        Génère une réponse JSON UNIQUEMENT avec exactement cette structure, sans markdown :
        {
          "narrative": "résumé narratif en 2-3 phrases avec les chiffres clés",
          "alerts": [
            {"type": "CRITIQUE|ATTENTION|POSITIF|OPPORTUNITE", "title": "titre court", "message": "description", "action": "label action"}
          ],
          "opportunities": [
            {"priority": 1, "title": "titre", "description": "détail", "impact": "Fort|Moyen|Faible"}
          ],
          "priorityActions": [
            {"priority": "P1|P2|P3|P4", "action": "description courte", "urgency": "Urgence|Cette semaine|Ce mois|Planifier"}
          ],
          "generatedAt": "%s"
        }
        Maximum: 4 alertes, 4 opportunités, 4 actions. Sois concis et actionnable. Langue: français.
        """.formatted(
                totalUsers, totalCitoyens, totalAgents, totalBanned,
                notVerified, verifiedRate, incompleteProfiles,
                newThisWeek, newLastWeek, growthPct,
                avgPoints, avgStreak, highRiskCount, ambassadeurCount,
                now.toString().substring(0, 16).replace("T", " à ")
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            Map<String, Object> requestBody = Map.of(
                    "model",       "llama-3.1-8b-instant",
                    "max_tokens",  800,
                    "temperature", 0.4,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    groqApiUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), Map.class
            );

            if (response.getBody() == null) {
                return ResponseEntity.status(502).body(Map.of("error", "Réponse vide"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");

            String text = "";
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message =
                        (Map<String, Object>) choices.get(0).get("message");
                if (message != null) text = (String) message.get("content");
            }

            text = text.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("```$", "")
                    .trim();

            return ResponseEntity.ok(Map.of("insights", text));

        } catch (Exception e) {
            System.err.println("[INSIGHTS] Exception: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "AI indisponible"));
        }
    }
}