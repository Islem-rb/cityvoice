package tn.cityvoice.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.userservice.client.NotificationClient;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.AgentStatus;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.repository.InvitationCodeRepository;
import tn.cityvoice.userservice.repository.PointTransactionRepository;
import tn.cityvoice.userservice.repository.UserBadgeRepository;
import tn.cityvoice.userservice.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    final UserRepository userRepository;
    final BCryptPasswordEncoder passwordEncoder;
    private final UserBadgeRepository userBadgeRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final InvitationCodeRepository invitationCodeRepository;
    final NotificationClient notificationClient;


    @Value("${app.reset-token.expiry-minutes:30}")
    int resetTokenExpiryMinutes;

    final EmailService emailService;

    @Override
    public User register(User user, String rawPassword) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmailVerified(false);
        user.setEmailVerificationToken(UUID.randomUUID().toString());
        User saved = userRepository.save(user);

        notificationClient.envoyer(
                saved.getId().toString(),
                "INFO",
                "👋 Bienvenue sur CityVoice, " + saved.getNom() + " !",
                null,
                null
        );
        return saved;
    }

    @Override
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }










    @Override
    public List<User> findByRole(String role) {
        return userRepository.findByRole(role);  // ← AJOUTER CETTE LIGNE
    }
    @Override
    public User update(UUID id, User updated) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + id));

        // Mettre à jour seulement les champs modifiables
        if (updated.getNom()         != null) existing.setNom(updated.getNom());
        if (updated.getEmail()       != null) existing.setEmail(updated.getEmail());
        if (updated.getTelephone()   != null) existing.setTelephone(updated.getTelephone());
        if (updated.getGouvernorat() != null) existing.setGouvernorat(updated.getGouvernorat());
        if (updated.getVille()       != null) existing.setVille(updated.getVille());
        if (updated.getCodePostal()  != null) existing.setCodePostal(updated.getCodePostal());
        if (updated.getPhoto() != null) {
            existing.setPhoto(updated.getPhoto().isBlank() ? null : updated.getPhoto());
        }

        // Ne jamais écraser : password, role, points, dateInscription
        return userRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        // 1. Dissocier les codes d'invitation utilisés par cet user
        //    (met usedByUser à null sans supprimer le code)
        invitationCodeRepository.clearUsedByUser(id);

        // 2. Supprimer les badges (cascade est déjà configurée dans User,
        //    mais on le fait explicitement pour être sûr)
        userBadgeRepository.deleteByUserId(id);

        // 3. Supprimer les transactions de points
        pointTransactionRepository.deleteByUserId(id);

        // 4. Supprimer l'utilisateur
        userRepository.deleteById(id);
    }

    @Override
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email introuvable"));

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes));
        userRepository.save(user);

        emailService.sendResetEmail(email, token, user.getNom());
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"));

        if (LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            throw new RuntimeException("Token expiré");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    @Override
    public void updatePhoto(UUID userId, String base64Photo) {
        User user = findById(userId);
        user.setPhoto(base64Photo);
        userRepository.save(user);
    }

    @Override
    public String getStatut(User user) {
        if (user.isBanned()) {
            return "SUSPENDU";
        }

        if (!user.isEmailVerified()) {
            return "EN_ATTENTE_VERIFICATION";
        }

        boolean incomplete =
                user.getNom() == null || user.getNom().isBlank() ||
                        user.getTelephone() == null || user.getTelephone().isBlank() ||
                        user.getGouvernorat() == null || user.getGouvernorat().isBlank() ||
                        user.getVille() == null || user.getVille().isBlank();

        // Pour les citoyens, on peut être un peu plus exigeant
        if (user.getRole() == Role.CITOYEN) {
            incomplete = incomplete || user.getPhoto() == null || user.getPhoto().isBlank();
        }

        if (incomplete) {
            return "INCOMPLET";
        }

        if (user.getDateInscription() != null &&
                user.getDateInscription().isAfter(LocalDateTime.now().minusDays(7))) {
            return "NOUVEAU";
        }

        return "ACTIF";
    }

    @Override
    public int getCivicIndex(User user) {
        // Réservé aux citoyens uniquement
        if (user.getRole() != Role.CITOYEN) return -1;

        int score = 0;

        // ── Complétion profil (30 pts max) ───────────────────
        if (user.getNom()         != null && !user.getNom().isBlank())         score += 5;
        if (user.getTelephone()   != null && !user.getTelephone().isBlank())   score += 5;
        if (user.getGouvernorat() != null && !user.getGouvernorat().isBlank()) score += 5;
        if (user.getVille()       != null && !user.getVille().isBlank())       score += 5;
        if (user.getPhoto()       != null && !user.getPhoto().isBlank())       score += 10;

        // ── Email vérifié (20 pts) ────────────────────────────
        if (user.isEmailVerified()) score += 20;

        // ── Points gamification (25 pts max) ─────────────────
        score += Math.min(25, user.getPoints() / 20);

        // ── Ancienneté (15 pts max) ───────────────────────────
        if (user.getDateInscription() != null) {
            long days = ChronoUnit.DAYS.between(
                    user.getDateInscription().toLocalDate(),
                    LocalDate.now()
            );
            if      (days > 365) score += 15;
            else if (days > 180) score += 10;
            else if (days > 30)  score += 5;
        }

        // ── Streak actif (10 pts max) ─────────────────────────
        score += Math.min(10, user.getLoginStreak());

        return Math.min(100, score);
    }

    @Override
    public void updateAgentStatus(UUID userId, AgentStatus status) {
        User user = findById(userId);
        // Seulement pour les agents
        if (user.getRole() == Role.CITOYEN || user.getRole() == Role.ADMIN_VILLE) {
            throw new RuntimeException("Statut agent non applicable");
        }
        user.setAgentStatus(status);
        userRepository.save(user);
    }
}