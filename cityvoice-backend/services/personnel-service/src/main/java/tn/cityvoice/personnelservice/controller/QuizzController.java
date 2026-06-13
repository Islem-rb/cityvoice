package tn.cityvoice.personnelservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.personnelservice.entity.CandidatureEquipe;
import tn.cityvoice.personnelservice.entity.CvUser;
import tn.cityvoice.personnelservice.entity.Fonction;
import tn.cityvoice.personnelservice.entity.MembreEquipe;
import tn.cityvoice.personnelservice.entity.QuizzResult;
import tn.cityvoice.personnelservice.feign.UserFeignClient;
import tn.cityvoice.personnelservice.repository.CandidatureEquipeRepository;
import tn.cityvoice.personnelservice.repository.CvUserRepository;
import tn.cityvoice.personnelservice.repository.EquipeRepository;
import tn.cityvoice.personnelservice.repository.MembreRepository;
import tn.cityvoice.personnelservice.repository.NotificationRepository;
import tn.cityvoice.personnelservice.repository.QuizzResultRepository;
import tn.cityvoice.personnelservice.service.NotificationService;
import tn.cityvoice.personnelservice.service.QuizzService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/personnel/quiz")
@RequiredArgsConstructor
@Slf4j
public class QuizzController {

    private final QuizzService          quizService;
    private final QuizzResultRepository quizResultRepository;
    private final CvUserRepository cvUserRepository;
    private final CandidatureEquipeRepository candidatureEquipeRepository;
    private final EquipeRepository equipeRepository;
    private final MembreRepository membreRepository;
    private final NotificationRepository notificationRepository;
    private final UserFeignClient userFeignClient;
    private final NotificationService notificationService;

    /**
     * Génère 10 questions QCM pour une fonction donnée.
     * GET /personnel/quiz/generate?fonction=Développeur+Java
     */
    @GetMapping("/generate")
    public ResponseEntity<?> generate(@RequestParam("fonction") String fonction) {
        log.info(">>> [Quiz] Génération pour la fonction : {}", fonction);
        List<Map<String, Object>> questions = quizService.generateQuiz(fonction);

        if (questions == null || questions.isEmpty()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Impossible de générer le quiz (Ollama indisponible ou réponse vide)"));
        }
        return ResponseEntity.ok(questions);
    }

    /**
     * Soumission des résultats du quiz.
     * POST /personnel/quiz/submit
     * Body : { userId, cvId, fonction, score, totalQuestions, timeExpired }
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody Map<String, Object> body) {
        try {
            UUID    userId      = UUID.fromString((String) body.get("userId"));
            UUID    cvId        = UUID.fromString((String) body.get("cvId"));
            String  fonction    = (String)  body.get("fonction");
            int     score       = ((Number) body.get("score")).intValue();
            int     totalQ      = ((Number) body.getOrDefault("totalQuestions", 10)).intValue();
            boolean timeExpired = Boolean.parseBoolean(
                    String.valueOf(body.getOrDefault("timeExpired", false)));

            CvUser cv = cvUserRepository.findById(cvId)
                    .orElseThrow(() -> new RuntimeException("CV introuvable"));

            CandidatureEquipe candidature = cv.getCandidature();
            if (candidature == null) {
                throw new RuntimeException("Candidature introuvable pour ce CV");
            }

            if (candidature.getDateExpiration() != null
                    && candidature.getDateExpiration().isBefore(LocalDate.now())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La candidature est expirée, test non autorisé"));
            }

            if (!cv.getUserId().equals(userId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Ce test ne correspond pas à l'utilisateur du CV"));
            }

            boolean preselected = notificationRepository.existsByReceiverIdAndCvIdAndType(
                    userId, cvId, "PRESELECTION"
            );
            if (!preselected) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Test non autorisé: candidature non présélectionnée"));
            }

            if (quizResultRepository.existsByCvIdAndUserId(cvId, userId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Vous avez déjà passé ce test pour cette candidature"));
            }

            QuizzResult result = new QuizzResult();
            result.setUserId(userId);
            result.setCvId(cvId);           // ← cvId maintenant persisté
            result.setFonction(fonction);
            result.setScore(score);
            result.setTotalQuestions(totalQ);
            result.setTimeExpired(timeExpired);
            quizResultRepository.save(result);

            log.info(">>> [Quiz] Résultat enregistré — userId={} cvId={} score={}/{}",
                    userId, cvId, score, totalQ);

            return ResponseEntity.ok(Map.of(
                    "message",     "Quiz soumis avec succès",
                    "score",       score,
                    "totalQ",      totalQ,
                    "timeExpired", timeExpired,
                    "percentage",  Math.round((double) score / totalQ * 100)
            ));

        } catch (Exception e) {
            log.error(">>> [Quiz] Erreur soumission : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Données invalides : " + e.getMessage()));
        }
    }

    /**
     * Récupère le dernier résultat de quiz pour un cvId.
     * GET /personnel/quiz/result/{cvId}
     */
    @GetMapping("/result/{cvId}")
    public ResponseEntity<?> getResult(@PathVariable("cvId") UUID cvId) {
        return quizResultRepository.findTopByCvIdOrderByPassedAtDesc(cvId)
                .map(r -> ResponseEntity.ok(Map.of(
                        "score",       r.getScore(),
                        "totalQ",      r.getTotalQuestions(),
                        "fonction",    r.getFonction(),
                        "timeExpired", r.isTimeExpired(),
                        "passedAt",    r.getPassedAt().toString(),
                        "percentage",  Math.round((double) r.getScore() / r.getTotalQuestions() * 100)
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Vérifie si un utilisateur a déjà passé le quiz pour un CV donné.
     * GET /personnel/quiz/check?cvId=...&userId=...
     */
    @GetMapping("/check")
    public ResponseEntity<?> checkAlreadyTaken(
            @RequestParam("cvId")   UUID cvId,
            @RequestParam("userId") UUID userId) {
        boolean taken = quizResultRepository.existsByCvIdAndUserId(cvId, userId);
        return ResponseEntity.ok(Map.of("taken", taken));
    }

    /**
     * Liste des candidats ayant déposé un CV pour une candidature donnée,
     * avec leur dernière note de test si disponible.
     * GET /personnel/quiz/candidature/{candidatureId}/results
     */
    @GetMapping("/candidature/{candidatureId}/results")
    public ResponseEntity<?> getResultsByCandidature(@PathVariable("candidatureId") UUID candidatureId) {
        CandidatureEquipe candidature = candidatureEquipeRepository.findById(candidatureId)
                .orElseThrow(() -> new RuntimeException("Candidature introuvable"));

        List<CvUser> cvs = candidature.getCvs();
        if (cvs == null || cvs.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<UUID> cvIds = cvs.stream().map(CvUser::getId).toList();
        Map<UUID, CvUser> cvById = cvs.stream().collect(Collectors.toMap(CvUser::getId, cv -> cv));

        // Récupérer tous les résultats (triés du plus récent au plus ancien)
        // puis garder le premier résultat par cvId (= dernier résultat du candidat)
        Map<UUID, QuizzResult> latestByCvId = quizResultRepository
                .findByCvIdInOrderByPassedAtDesc(cvIds)
                .stream()
                .collect(Collectors.toMap(
                        QuizzResult::getCvId,
                        r -> r,
                        (existing, ignored) -> existing
                ));

        // Retourner 1 ligne par CV déposé (même si aucun test n'a été passé)
        List<Map<String, Object>> payload = cvs.stream().map(cv -> {
            QuizzResult result = latestByCvId.get(cv.getId());

            String nom = "Inconnu";
            String email = null;
            String telephone = null;
            String photo = null;
            try {
                Map<String, Object> user = userFeignClient.getUserById(cv.getUserId());
                nom = String.valueOf(user.getOrDefault("nom", "Inconnu"));
                email = user.get("email") != null ? String.valueOf(user.get("email")) : null;
                telephone = user.get("telephone") != null ? String.valueOf(user.get("telephone")) : null;
                photo = user.get("photo") != null ? String.valueOf(user.get("photo")) : null;
            } catch (Exception e) {
                log.warn("Impossible de récupérer le user {}: {}", cv.getUserId(), e.getMessage());
            }

            Map<String, Object> dto = new java.util.LinkedHashMap<>();
            dto.put("cvId", cv.getId().toString());
            dto.put("userId", cv.getUserId().toString());
            dto.put("nom", nom);
            dto.put("email", email == null ? "" : email);
            dto.put("telephone", telephone == null ? "" : telephone);
            dto.put("photo", photo == null ? "" : photo);

            if (result != null) {
                dto.put("resultId", result.getId().toString());
                dto.put("score", result.getScore());
                dto.put("totalQuestions", result.getTotalQuestions());
                dto.put("fonction", result.getFonction());
                dto.put("timeExpired", result.isTimeExpired());
                dto.put("passedAt", result.getPassedAt().toString());
            } else {
                dto.put("resultId", "");
                dto.put("score", null);
                dto.put("totalQuestions", null);
                dto.put("fonction", "");
                dto.put("timeExpired", false);
                dto.put("passedAt", "");
            }
            return dto;
        }).toList();

        return ResponseEntity.ok(payload);
    }

    /**
     * Ajouter un candidat à l'équipe liée à la candidature depuis un résultat quiz.
     * POST /personnel/quiz/candidature/{candidatureId}/hire
     */
    @PostMapping("/candidature/{candidatureId}/hire")
    public ResponseEntity<?> hireFromQuiz(
            @PathVariable("candidatureId") UUID candidatureId,
            @RequestBody Map<String, String> body) {

        UUID resultId = UUID.fromString(body.get("resultId"));
        String fonctionRequested = body.getOrDefault("fonctionMembre", "TECHNICIEN");

        CandidatureEquipe candidature = candidatureEquipeRepository.findById(candidatureId)
                .orElseThrow(() -> new RuntimeException("Candidature introuvable"));
        if (candidature.getEquipe() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Aucune équipe liée à cette candidature"));
        }

        QuizzResult result = quizResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("Résultat quiz introuvable"));

        // Empêcher le recrutement si l'utilisateur est déjà membre d'une équipe
        if (membreRepository.existsByUserId(result.getUserId())) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Utilisateur déjà recruté dans une équipe"));
        }

        CvUser cv = cvUserRepository.findById(result.getCvId())
                .orElseThrow(() -> new RuntimeException("CV introuvable"));

        if (cv.getCandidature() == null || !candidatureId.equals(cv.getCandidature().getId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ce résultat quiz n'appartient pas à cette candidature"));
        }

        Map<String, Object> user = userFeignClient.getUserById(result.getUserId());
        String nomComplet = String.valueOf(user.getOrDefault("nom", "Candidat"));
        String[] parts = nomComplet.trim().split("\\s+");
        String prenom = parts.length > 0 ? parts[0] : "Candidat";
        String nom = parts.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)) : prenom;

        Fonction fonction = Fonction.valueOf(fonctionRequested.toUpperCase());

        MembreEquipe membre = new MembreEquipe();
        membre.setUserId(result.getUserId());
        membre.setNom(nom);
        membre.setPrenom(prenom);
        membre.setFonction(fonction);
        membre.setEmail(user.get("email") != null ? String.valueOf(user.get("email")) : null);
        membre.setTelephone(user.get("telephone") != null ? String.valueOf(user.get("telephone")) : null);
        membre.setPhoto(user.get("photo") != null ? String.valueOf(user.get("photo")) : null);

        // Fallback: empêcher doublon par email
        String emailCandidate = membre.getEmail();
        if (emailCandidate != null && !emailCandidate.isBlank()
                && membreRepository.existsByEmailIgnoreCase(emailCandidate)) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Utilisateur déjà recruté dans une équipe"));
        }

        MembreEquipe savedMembre = membreRepository.save(membre);
        if (candidature.getEquipe().getMembresEquipe() == null) {
            candidature.getEquipe().setMembresEquipe(new java.util.ArrayList<>());
        }
        candidature.getEquipe().getMembresEquipe().add(savedMembre);
        equipeRepository.save(candidature.getEquipe());

        notificationService.sendNotification(
                result.getUserId(),
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "Bienvenue dans l'équipe " + candidature.getEquipe().getName(),
                "Félicitations ! Vous avez été ajouté(e) à l'équipe « " + candidature.getEquipe().getName() + " ».",
                "EQUIPE_AJOUT",
                result.getCvId(),
                result.getFonction()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Candidat ajouté à l'équipe avec succès",
                "membreId", savedMembre.getId().toString()
        ));
    }
}