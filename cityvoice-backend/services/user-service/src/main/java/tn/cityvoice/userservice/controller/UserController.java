package tn.cityvoice.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.AgentStatus;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.entity.enums.PointReason;
import tn.cityvoice.userservice.repository.PointTransactionRepository;
import tn.cityvoice.userservice.repository.UserRepository;
import tn.cityvoice.userservice.service.BadgeService;
import tn.cityvoice.userservice.service.PhotoModerationService;
import tn.cityvoice.userservice.service.PointService;
import tn.cityvoice.userservice.service.UserService;
import tn.cityvoice.userservice.entity.enums.PointReason;
import lombok.extern.slf4j.Slf4j;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    // ============================================================
    // DEPENDENCIES
    // ============================================================
    private final UserService                       userService;
    private final UserRepository                    userRepository;
    private final PointService                      pointService;
    private final BadgeService                      badgeService;
    private final PhotoModerationService            photoModerationService;
    private final PointTransactionRepository        pointTransactionRepository;
    private final RestTemplate                      restTemplate;

    @Value("${services.ai.url:http://localhost:8081}")
    private String aiServiceUrl;

    // ============================================================
    // BASIC CRUD
    // ============================================================

    @GetMapping
    public List<User> getAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable("id") UUID id) {
        User user = userService.findById(id);

        // Calcul des points du mois en cours
        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        int monthlyPoints = 0;
        if (user.getRole() == Role.CITOYEN) {
            monthlyPoints = pointTransactionRepository
                    .sumPositivePointsSince(id, startOfMonth);
        }

        // Construire la réponse enrichie
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id",              user.getId());
        response.put("nom",             user.getNom());
        response.put("email",           user.getEmail());
        response.put("telephone",       user.getTelephone());
        response.put("role",            user.getRole());
        response.put("points",          user.getPoints());
        response.put("gouvernorat",     user.getGouvernorat());
        response.put("ville",           user.getVille());
        response.put("codePostal",      user.getCodePostal());
        response.put("photo",           user.getPhoto());
        response.put("dateInscription", user.getDateInscription());
        response.put("banned",          user.isBanned());
        response.put("banReason",       user.getBanReason());
        response.put("loginStreak",     user.getLoginStreak());
        response.put("trustLevel",      user.getTrustLevel());
        response.put("emailVerified",   user.isEmailVerified());
        response.put("agentStatus",     user.getAgentStatus());

        // ── Champs calculés ───────────────────────────────────
        response.put("statut",          userService.getStatut(user));
        response.put("civicIndex",      userService.getCivicIndex(user));
        response.put("monthlyPoints",   monthlyPoints);

        // whatsapp
        response.put("whatsappNotifs",    user.isWhatsappNotifs());
        response.put("smsNotifs",         user.isSmsNotifs());
        response.put("callmebotApiKey",   user.getCallmebotApiKey() != null
                ? user.getCallmebotApiKey() : "");

        // ── Online/Offline status ─────────────────────────────
        response.put("lastSeenAt",      user.getLastSeenAt());
        response.put("isOnline",        isUserOnline(user));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") UUID id) {
        try {
            userService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "DELETE_FAILED",
                    "message", e.getMessage()
            ));
        }
    }


    // ============================================================
    // UPDATE USER (with auto-rewards + name screening)
    // ============================================================

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            User existing = userService.findById(id);

            // ══════════════════════════════════════════════════════
            // NAME SCREENING ON UPDATE
            // ══════════════════════════════════════════════════════
            if (body.get("nom") != null) {
                String newName = (String) body.get("nom");

                try {
                    Map<String, Object> screenResult = restTemplate.postForObject(
                            aiServiceUrl + "/api/ai/screen-name",
                            Map.of("name", newName),
                            Map.class
                    );

                    if (screenResult != null &&
                            Boolean.FALSE.equals(screenResult.get("appropriate"))) {
                        return ResponseEntity.status(400).body(Map.of(
                                "error", "NAME_REJECTED",
                                "message", screenResult.getOrDefault("reason", "Nom inapproprié")
                        ));
                    }
                } catch (Exception e) {
                    // Fail open if AI service unavailable
                    System.out.println("Name screening unavailable: " + e.getMessage());
                }

                existing.setNom(newName);
            }

            // Update other basic fields
            if (body.get("telephone")   != null) existing.setTelephone((String) body.get("telephone"));
            if (body.get("gouvernorat") != null) existing.setGouvernorat((String) body.get("gouvernorat"));
            if (body.get("ville")       != null) existing.setVille((String) body.get("ville"));
            if (body.get("codePostal")  != null) existing.setCodePostal((String) body.get("codePostal"));

            // ══════════════════════════════════════════════════════
            // PHOTO MODERATION ON UPDATE
            // ══════════════════════════════════════════════════════
            if (body.containsKey("photo")) {
                String photo = (String) body.get("photo");

                if (photo != null && !photo.isBlank()) {
                    PhotoModerationService.ModerationResult result =
                            photoModerationService.moderate(photo);

                    if (!result.safe()) {
                        return ResponseEntity.status(400).body(Map.of(
                                "error", "PHOTO_REJECTED",
                                "message", result.reason()
                        ));
                    }
                }

                existing.setPhoto(photo == null || photo.isBlank() ? null : photo);
            }

            User saved = userRepository.save(existing);

            // Reward: photo added (once)
            if (existing.getPhoto() != null &&
                    !pointService.hasReason(id, PointReason.PHOTO_AJOUTEE)) {
                pointService.rewardPhotoAjoutee(saved);
            }

            // Reward: profile complete (once)
            if (!pointService.hasReason(id, PointReason.PROFIL_COMPLETE)) {
                boolean isComplete = saved.getNom()         != null &&
                        saved.getTelephone()   != null &&
                        saved.getGouvernorat() != null &&
                        saved.getVille()       != null &&
                        saved.getPhoto()       != null;
                if (isComplete) {
                    pointService.rewardProfilComplete(saved);
                }
            }

            badgeService.checkAndAwardBadges(saved);

            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/photo")
    public ResponseEntity<?> updatePhoto(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body) {
        String photo = body.get("photo");

        if (photo == null || photo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Photo requise"
            ));
        }

        // ══════════════════════════════════════════════════════
        // PHOTO MODERATION (CRITICAL FIX)
        // ══════════════════════════════════════════════════════
        PhotoModerationService.ModerationResult result =
                photoModerationService.moderate(photo);

        if (!result.safe()) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", "PHOTO_REJECTED",
                    "message", result.reason()
            ));
        }

        try {
            userService.updatePhoto(id, photo);
            return ResponseEntity.ok(Map.of("message", "Photo mise à jour"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }


    // ============================================================
    // ROLE MANAGEMENT
    // ============================================================

    @GetMapping("/by-role/{role}")
    public ResponseEntity<List<User>> getByRole(@PathVariable("role") String role) {
        try {
            Role r = Role.valueOf(role.toUpperCase());
            List<User> users = userService.findAll().stream()
                    .filter(u -> u.getRole() == r)
                    .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(users);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body) {
        try {
            Role role = Role.valueOf(body.get("role"));
            User user = userService.findById(id);
            user.setRole(role);
            return ResponseEntity.ok(userService.update(id, user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    // ============================================================
    // BAN / UNBAN
    // ============================================================

    @PatchMapping("/{id}/ban")
    public ResponseEntity<?> banUser(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body) {
        User user = userService.findById(id);
        user.setBanned(true);
        user.setBanReason(body.getOrDefault("reason", "Violation des conditions d'utilisation"));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Utilisateur banni"));
    }

    @PatchMapping("/{id}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable("id") UUID id) {
        User user = userService.findById(id);
        user.setBanned(false);
        user.setBanReason(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Utilisateur débanni"));
    }


    // ============================================================
    // LEADERBOARD & PUBLIC PROFILE
    // ============================================================

    @GetMapping("/leaderboard")
    public ResponseEntity<List<User>> getLeaderboard(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        List<User> all = userRepository.findAll();
        return ResponseEntity.ok(
                all.stream()
                        .filter(u -> u.getRole() == Role.CITOYEN)
                        .filter(u -> !u.isBanned())
                        .sorted((a, b) -> Integer.compare(b.getPoints(), a.getPoints()))
                        .limit(limit)
                        .collect(java.util.stream.Collectors.toList())
        );
    }

    @GetMapping("/{id}/public")
    public ResponseEntity<?> getPublicProfile(@PathVariable("id") UUID id) {
        User user = userService.findById(id);

        Map<String, Object> profile = new java.util.LinkedHashMap<>();
        profile.put("id",              user.getId());
        profile.put("nom",             user.getNom());
        profile.put("photo",           user.getPhoto());
        profile.put("role",            user.getRole());
        profile.put("points",          user.getPoints());
        profile.put("trustLevel",      user.getTrustLevel());
        profile.put("gouvernorat",     user.getGouvernorat());
        profile.put("ville",           user.getVille());
        profile.put("dateInscription", user.getDateInscription());
        profile.put("loginStreak",     user.getLoginStreak());


        return ResponseEntity.ok(profile);
    }


    // ============================================================
    // PAGINATION & SEARCH
    // ============================================================

    @GetMapping("/paginated")
    public ResponseEntity<?> getPaginated(
            @RequestParam(name = "page",   defaultValue = "0")  int    page,
            @RequestParam(name = "size",   defaultValue = "10") int    size,
            @RequestParam(name = "search", required = false)    String search,
            @RequestParam(name = "role",   required = false)    String role) {

        List<User> all = userRepository.findAll();
        all = all.stream()
                .filter(u -> u.getRole() != Role.ADMIN_VILLE)
                .toList();

        // Filter by role
        if (role != null && !role.isBlank() && !role.equals("ALL")) {
            try {
                Role r = Role.valueOf(role);
                all = all.stream().filter(u -> u.getRole() == r).toList();
            } catch (IllegalArgumentException ignored) {}
        }

        // Filter by search term
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            all = all.stream().filter(u ->
                    (u.getNom()   != null && u.getNom().toLowerCase().contains(q))   ||
                            (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                            (u.getVille() != null && u.getVille().toLowerCase().contains(q))
            ).toList();
        }

        // Sort by date desc
        all = all.stream()
                .sorted((a, b) -> b.getDateInscription().compareTo(a.getDateInscription()))
                .toList();

        // Paginate
        int total = all.size();
        int start = page * size;
        int end   = Math.min(start + size, total);
        List<User> pageContent = start >= total ? List.of() : all.subList(start, end);

        // ══ BUILD ENRICHED RESPONSE WITH CALCULATED FIELDS ════════
        List<Map<String, Object>> enrichedContent = pageContent.stream().map(user -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();

            // All entity fields
            map.put("id",              user.getId());
            map.put("nom",             user.getNom());
            map.put("email",           user.getEmail());
            map.put("telephone",       user.getTelephone());
            map.put("role",            user.getRole());
            map.put("points",          user.getPoints());
            map.put("gouvernorat",     user.getGouvernorat());
            map.put("ville",           user.getVille());
            map.put("codePostal",      user.getCodePostal());
            map.put("photo",           user.getPhoto());
            map.put("dateInscription", user.getDateInscription());
            map.put("banned",          user.isBanned());
            map.put("banReason",       user.getBanReason());
            map.put("loginStreak",     user.getLoginStreak());
            map.put("trustLevel",      user.getTrustLevel());
            map.put("emailVerified",   user.isEmailVerified());
            map.put("agentStatus",     user.getAgentStatus());

            // ── Calculated fields ─────────────────────────────────
            map.put("statut",          userService.getStatut(user));
            map.put("civicIndex",      userService.getCivicIndex(user));

            // ── Online status ─────────────────────────────────────
            map.put("lastSeenAt",      user.getLastSeenAt());
            map.put("isOnline",        isUserOnline(user));

            return map;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "content",       pageContent,
                "totalElements", total,
                "totalPages",    (int) Math.ceil((double) total / size),
                "currentPage",   page
        ));
    }

    // ══ HELPER METHOD ═══════════════════════════════════════════
    private boolean isUserOnline(User user) {
        if (user.getLastSeenAt() == null) {
            return false;
        }
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        return user.getLastSeenAt().isAfter(fiveMinutesAgo);
    }

    // ============================================================
    // STATUT AGENT
    // ============================================================

    // ── Statut calculé ────────────────────────────────────────
    @GetMapping("/{id}/statut")
    public ResponseEntity<?> getStatut(@PathVariable("id") UUID id) {
        User user = userService.findById(id);
        return ResponseEntity.ok(Map.of(
                "statut",     userService.getStatut(user),
                "civicIndex", userService.getCivicIndex(user)
        ));
    }

    // ── Mettre à jour le statut agent ────────────────────────
    @PatchMapping("/{id}/agent-status")
    public ResponseEntity<?> updateAgentStatus(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body) {
        try {
            AgentStatus status = AgentStatus.valueOf(body.get("status"));
            userService.updateAgentStatus(id, status);
            return ResponseEntity.ok(Map.of("message", "Statut mis à jour"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Statut invalide");
        }
    }


    // ============================================================
    // WHATSAPP — CALLMEBOT API KEY
    // ============================================================

    @PatchMapping("/{id}/callmebot-key")
    public ResponseEntity<?> saveCallmebotKey(
            @PathVariable("id") UUID id,
            @RequestBody java.util.Map<String, String> body) {
        try {
            User user = userService.findById(id);
            String key = body.getOrDefault("callmebotApiKey", "").trim();

            if (key.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "KEY_REQUIRED",
                          "message", "La clé API CallMeBot ne peut pas être vide."));
            }

            user.setCallmebotApiKey(key);
            userRepository.save(user);
            return ResponseEntity.ok(java.util.Map.of(
                "callmebotApiKey", user.getCallmebotApiKey(),
                "message", "Clé CallMeBot enregistrée ✓"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // WHATSAPP NOTIFICATIONS TOGGLE
    // ============================================================

    @PatchMapping("/{id}/whatsapp-notifs")
    public ResponseEntity<?> toggleWhatsappNotifs(
            @PathVariable("id") UUID id,
            @RequestBody java.util.Map<String, Boolean> body) {
        try {
            User user = userService.findById(id);
            boolean enabled = Boolean.TRUE.equals(body.get("whatsappNotifs"));

            if (enabled && (user.getTelephone() == null || user.getTelephone().isBlank())) {
                return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "PHONE_REQUIRED",
                          "message", "Ajoutez un numéro de téléphone avant d'activer WhatsApp."));
            }

            user.setWhatsappNotifs(enabled);
            userRepository.save(user);
            return ResponseEntity.ok(java.util.Map.of(
                "whatsappNotifs", user.isWhatsappNotifs(),
                "message", enabled ? "Notifications WhatsApp activées ✓" : "Notifications WhatsApp désactivées"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    // ============================================================
    // SMS NOTIFICATIONS TOGGLE
    // ============================================================

    @PatchMapping("/{id}/sms-notifs")
    public ResponseEntity<?> toggleSmsNotifs(
            @PathVariable("id") UUID id,
            @RequestBody java.util.Map<String, Boolean> body) {
        try {
            User user = userService.findById(id);
            boolean enabled = Boolean.TRUE.equals(body.get("smsNotifs"));

            if (enabled && (user.getTelephone() == null || user.getTelephone().isBlank())) {
                return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "PHONE_REQUIRED",
                          "message", "Ajoutez un numéro de téléphone avant d'activer le SMS."));
            }

            user.setSmsNotifs(enabled);
            userRepository.save(user);
            return ResponseEntity.ok(java.util.Map.of(
                "smsNotifs", user.isSmsNotifs(),
                "message", enabled ? "Notifications SMS activées ✓" : "Notifications SMS désactivées"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    //projet
    @GetMapping("/by-email")
    public ResponseEntity<?> getByEmail(@RequestParam(name = "email") String email) {
        try {
            // Decode URL-encoded email (convert %40 back to @)
            String decodedEmail = URLDecoder.decode(email, StandardCharsets.UTF_8);
            log.info("Looking for user with email: {}", decodedEmail);

            Optional<User> userOpt = userRepository.findByEmail(decodedEmail);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, String> response = Map.of("id", user.getId().toString());
                log.info("Found user by email: {}", decodedEmail);
                return ResponseEntity.ok(response);
            } else {
                log.warn("User not found by email: {}", decodedEmail);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found with email: " + decodedEmail));
            }
        } catch (Exception e) {
            log.error("Error in /by-email: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    // ============================================================
    // TEST ENDPOINT
    // ============================================================

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("user-service OK");
    }

}