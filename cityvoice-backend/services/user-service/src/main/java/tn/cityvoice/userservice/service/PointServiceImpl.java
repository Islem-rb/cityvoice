package tn.cityvoice.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tn.cityvoice.userservice.client.NotificationClient;
import tn.cityvoice.userservice.entity.PointTransaction;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.PointReason;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.repository.PointTransactionRepository;
import tn.cityvoice.userservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor

public class PointServiceImpl implements PointService {

    final PointTransactionRepository pointRepo;
    final UserRepository             userRepo;
    final NotificationClient notificationClient;

    @Lazy
    final TrustService trustService;

    @Override
    public PointTransaction addPoints(User user, int points, PointReason reason, String description) {

        if (user.getRole() != Role.CITOYEN) {
            return null;
        }

        int oldPoints = user.getPoints();
        int newTotal  = oldPoints + points;

        PointTransaction tx = new PointTransaction();
        tx.setUser(user);
        tx.setPoints(points);
        tx.setReason(reason);
        tx.setDescription(description);
        pointRepo.save(tx);

        user.setPoints(newTotal);
        userRepo.save(user);

        trustService.updateIfChanged(user);

        if ((newTotal >= 100  && oldPoints < 100)  ||
                (newTotal >= 500  && oldPoints < 500)  ||
                (newTotal >= 1000 && oldPoints < 1000)) {

            notificationClient.envoyer(
                    user.getId().toString(),
                    "INFO",
                    "🏆 Vous avez atteint " + newTotal + " points !",
                    "/user/mes-points",
                    null
            );
        }

        return tx;
    }
    @Override
    public void rewardInscription(User user) {
        addPoints(user, 10, PointReason.INSCRIPTION,
                "Bienvenue sur CityVoice ! 🎉");
    }

    @Override
    public void rewardProfilComplete(User user) {
        addPoints(user, 25, PointReason.PROFIL_COMPLETE,
                "Profil complété — toutes les informations renseignées");
    }

    @Override
    public void rewardPhotoAjoutee(User user) {
        addPoints(user, 10, PointReason.PHOTO_AJOUTEE,
                "Photo de profil ajoutée");
    }

    @Override
    public void rewardPremiereConnexion(User user) {
        addPoints(user, 5, PointReason.PREMIERE_CONNEXION,
                "Première connexion à la plateforme");
    }

    @Override
    public List<PointTransaction> getTransactions(UUID userId) {
        return pointRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public boolean hasReason(UUID userId, PointReason reason) {
        return pointRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .anyMatch(t -> t.getReason() == reason);
    }







    public void addPoints(UUID userId, int points, String reason) {
        log.info("[POINTS] Début addPoints userId={} points={} reason={}", userId, points, reason);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> {
                    log.error("[POINTS] User not found: {}", userId);
                    return new RuntimeException("User not found");
                });
        log.info("[POINTS] User trouvé: {} points actuels={}", user.getEmail(), user.getPoints());

        if (user.getRole() != Role.CITOYEN) {
            log.info("[POINTS] Ignoré — rôle non citoyen: {}", user.getRole());
            return;
        }

        int oldPoints = user.getPoints();
        int newTotal  = oldPoints + points;

        try {
            PointTransaction tx = new PointTransaction();
            tx.setUser(user);
            tx.setPoints(points);
            tx.setReason(PointReason.valueOf(reason));
            tx.setDescription("Signalement soumis");
            pointRepo.save(tx);
            log.info("[POINTS] Transaction sauvegardée");
        } catch (Exception e) {
            log.error("[POINTS] Erreur création transaction: {}", e.getMessage(), e);
            throw e;
        }

        try {
            user.setPoints(newTotal);
            userRepo.save(user);
            log.info("[POINTS] Total mis à jour: {} → {}", oldPoints, newTotal);
        } catch (Exception e) {
            log.error("[POINTS] Erreur mise à jour total: {}", e.getMessage(), e);
            throw e;
        }

        trustService.updateIfChanged(user);

        if ((newTotal >= 100  && oldPoints < 100)  ||
                (newTotal >= 500  && oldPoints < 500)  ||
                (newTotal >= 1000 && oldPoints < 1000)) {

            notificationClient.envoyer(
                    user.getId().toString(),
                    "INFO",
                    "🏆 Vous avez atteint " + newTotal + " points !",
                    "/user/mes-points",
                    null
            );
            log.info("[POINTS] Notification milestone envoyée: {} pts", newTotal);
        }

        log.info("[POINTS] addPoints terminé avec succès");
    }
}