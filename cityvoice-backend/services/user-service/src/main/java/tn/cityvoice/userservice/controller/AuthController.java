package tn.cityvoice.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.userservice.config.JwtUtil;
import tn.cityvoice.userservice.entity.InvitationCode;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.repository.InvitationCodeRepository;
import tn.cityvoice.userservice.repository.UserRepository;
import tn.cityvoice.userservice.service.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    // ============================================================
    // DEPENDENCIES
    // ============================================================
    private final UserService               userService;
    private final UserRepository            userRepository;
    private final BCryptPasswordEncoder     passwordEncoder;
    private final InvitationCodeRepository  invitationCodeRepository;
    private final PointService              pointService;
    private final BadgeService              badgeService;
    private final EmailService              emailService;
    private final StreakService             streakService;
    private final PhotoModerationService    photoModerationService;
    private final RestTemplate              restTemplate;

    @Value("${services.ai.url:http://localhost:8081}")
    private String aiServiceUrl;

    // ============================================================
    // REGISTRATION
    // ============================================================

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {

        // Extract data
        String nom         = (String) body.get("nom");
        String email       = (String) body.get("email");
        String password    = (String) body.get("password");
        String telephone   = (String) body.get("telephone");
        String gouvernorat = (String) body.get("gouvernorat");
        String ville       = (String) body.get("ville");
        String codePostal  = (String) body.get("codePostal");
        String photo       = (String) body.get("photo");
        String roleStr     = (String) body.getOrDefault("role", "CITOYEN");
        String inviteCode  = (String) body.get("invitationCode");

        // Validate role
        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Rôle invalide : " + roleStr);
        }

        // Validate invitation code for non-citizen roles
        boolean isAgent = role != Role.CITOYEN;
        InvitationCode invitationCodeEntity = null;

        if (isAgent) {
            if (inviteCode == null || inviteCode.isBlank()) {
                return ResponseEntity.status(403)
                        .body("Un code d'invitation est requis pour ce rôle");
            }

            Optional<InvitationCode> codeOpt =
                    invitationCodeRepository.findByCode(inviteCode.toUpperCase());

            if (codeOpt.isEmpty() || !codeOpt.get().isValid()) {
                return ResponseEntity.status(403)
                        .body("Code d'invitation invalide ou expiré");
            }

            if (codeOpt.get().getRole() != role) {
                return ResponseEntity.status(403)
                        .body("Ce code ne correspond pas au rôle sélectionné");
            }

            invitationCodeEntity = codeOpt.get();
        }

        // ══════════════════════════════════════════════════════
        // NAME SCREENING (with config URL)
        // ══════════════════════════════════════════════════════
        try {
            Map<String, Object> screenResult = restTemplate.postForObject(
                    aiServiceUrl + "/api/ai/screen-name",
                    Map.of("name", nom),
                    Map.class
            );
            if (screenResult != null &&
                    Boolean.FALSE.equals(screenResult.get("appropriate"))) {
                String reason = (String) screenResult.getOrDefault(
                        "reason", "Nom inapproprié");
                return ResponseEntity.status(400)
                        .body("Nom refusé : " + reason);
            }
        } catch (Exception e) {
            // Fail open — continuer si le service IA est indisponible
            System.out.println("Name screening unavailable: " + e.getMessage());
        }

        // Create user
        User user = new User();
        user.setNom(nom);
        user.setEmail(email);
        user.setTelephone(telephone);
        user.setGouvernorat(gouvernorat);
        user.setVille(ville);
        user.setCodePostal(codePostal);
        user.setPhoto(photo);
        user.setRole(role);

        try {
            User created = userService.register(user, password);

            // Mark invitation code as used
            if (invitationCodeEntity != null) {
                invitationCodeEntity.setUsed(true);
                invitationCodeEntity.setUsedByUser(created);
                invitationCodeRepository.save(invitationCodeEntity);
            }

            // Rewards
            pointService.rewardInscription(created);
            badgeService.awardBadge(created, "FIRST_LOGIN");
            badgeService.checkAndAwardBadges(created);

            // Send verification email
            emailService.sendVerificationEmail(
                    created.getEmail(),
                    created.getEmailVerificationToken(),
                    created.getNom()
            );

            return ResponseEntity.ok(Map.of(
                    "message",       "Compte créé. Vérifiez votre email.",
                    "userId",        created.getId().toString(),
                    "role",          created.getRole().name(),
                    "emailVerified", false
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ============================================================
    // LOGIN
    // ============================================================

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String password = body.get("password");

        try {
            User user = userService.findByEmail(email);

            // Check password
            if (!passwordEncoder.matches(password, user.getPassword())) {
                return ResponseEntity.status(401).body("Mot de passe incorrect");
            }

            // Check if banned
            if (user.isBanned()) {
                return ResponseEntity.status(403).body(Map.of(
                        "error",     "USER_BANNED",
                        "message",   "Votre compte a été suspendu",
                        "banReason", user.getBanReason() != null ? user.getBanReason() : "Violation des conditions"
                ));
            }

            // Check if email verified
            if (!user.isEmailVerified()) {
                return ResponseEntity.status(403).body(Map.of(
                        "error",   "EMAIL_NOT_VERIFIED",
                        "message", "Veuillez vérifier votre email avant de vous connecter",
                        "email",   email
                ));
            }

            // Generate token and update streak
            String token = JwtUtil.generateToken(email);
            streakService.updateStreak(user);

            return ResponseEntity.ok(Map.of(
                    "token",  token,
                    "userId", user.getId().toString(),
                    "role",   user.getRole().name()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body("Identifiants invalides");
        }
    }

    // ============================================================
    // EMAIL VERIFICATION
    // ============================================================

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam(name = "token") String token) {
        Optional<User> userOpt = userRepository.findByEmailVerificationToken(token);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Token invalide");
        }

        User user = userOpt.get();

        if (user.isEmailVerified()) {
            return ResponseEntity.ok(Map.of("message", "Email déjà vérifié"));
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Email vérifié avec succès",
                "userId",  user.getId().toString(),
                "role",    user.getRole().name()
        ));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        try {
            User user = userService.findByEmail(email);
            if (user.isEmailVerified()) {
                return ResponseEntity.ok(Map.of("message", "Email déjà vérifié"));
            }
            // Generate new token
            user.setEmailVerificationToken(UUID.randomUUID().toString());
            userRepository.save(user);
            emailService.sendVerificationEmail(email, user.getEmailVerificationToken(), user.getNom());
            return ResponseEntity.ok(Map.of("message", "Email renvoyé"));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("message", "Si ce compte existe, un email a été envoyé"));
        }
    }

    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(
            @RequestParam(name = "email") String email) {
        return ResponseEntity.ok(Map.of(
                "exists", userRepository.existsByEmail(email)
        ));
    }

    @PostMapping("/moderate-photo")
    public ResponseEntity<?> moderatePhoto(
            @RequestBody Map<String, String> body) {
        String photo = body.get("photo");
        var result = photoModerationService.moderate(photo);
        return ResponseEntity.ok(Map.of(
                "safe",   result.safe(),
                "reason", result.reason() != null ? result.reason() : ""
        ));
    }

    // ============================================================
    // PASSWORD MANAGEMENT
    // ============================================================

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token   = authHeader.substring(7);
            String email   = JwtUtil.getSubject(token);
            String current = body.get("currentPassword");
            String newPwd  = body.get("newPassword");

            User user = userService.findByEmail(email);

            if (!passwordEncoder.matches(current, user.getPassword())) {
                return ResponseEntity.status(400).body("Mot de passe actuel incorrect");
            }

            user.setPassword(passwordEncoder.encode(newPwd));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Mot de passe modifié"));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email requis");
        }
        try {
            userService.forgotPassword(email);
            return ResponseEntity.ok(Map.of(
                    "message", "Email de réinitialisation envoyé"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of(
                    "message", "Si cet email existe, un lien a été envoyé"
            ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token    = body.get("token");
        String password = body.get("password");

        if (token == null || password == null) {
            return ResponseEntity.badRequest().body("Token et mot de passe requis");
        }
        try {
            userService.resetPassword(token, password);
            return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    // ============================================================
    // OAUTH2 (Google / Facebook) — CRITICAL FIX
    // ============================================================

    @PostMapping("/oauth2/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String nom   = body.get("name");
        String photo = body.get("photo");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email requis");
        }

        User user;
        try {
            user = userService.findByEmail(email);
        } catch (RuntimeException e) {
            // User doesn't exist — create new one
            User newUser = new User();
            newUser.setNom(nom != null ? nom : email.split("@")[0]);
            newUser.setEmail(email);
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setRole(Role.CITOYEN);
            newUser.setPhoto(photo);
            newUser.setEmailVerified(true); // ← CRITICAL FIX: OAuth2 emails are pre-verified

            try {
                user = userService.register(newUser, UUID.randomUUID().toString());
            } catch (RuntimeException ex) {
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        }

        String token = JwtUtil.generateToken(email);
        return ResponseEntity.ok(Map.of(
                "token",  token,
                "userId", user.getId().toString(),
                "role",   user.getRole().name()
        ));
    }

    @PostMapping("/oauth2/facebook")
    public ResponseEntity<?> facebookLogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String nom   = body.get("name");
        String photo = body.get("photo");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email requis");
        }

        User user;
        try {
            user = userService.findByEmail(email);
        } catch (RuntimeException e) {
            User newUser = new User();
            newUser.setNom(nom != null ? nom : email.split("@")[0]);
            newUser.setEmail(email);
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setRole(Role.CITOYEN);
            newUser.setPhoto(photo);
            newUser.setEmailVerified(true); // ← CRITICAL FIX

            try {
                user = userService.register(newUser, UUID.randomUUID().toString());
            } catch (RuntimeException ex) {
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        }

        String token = JwtUtil.generateToken(email);
        return ResponseEntity.ok(Map.of(
                "token",  token,
                "userId", user.getId().toString(),
                "role",   user.getRole().name()
        ));
    }
}