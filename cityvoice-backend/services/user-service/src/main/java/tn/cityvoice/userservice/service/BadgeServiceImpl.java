package tn.cityvoice.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.userservice.client.NotificationClient;
import tn.cityvoice.userservice.entity.Badge;
import tn.cityvoice.userservice.entity.UserBadge;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.BadgeCategory;
import tn.cityvoice.userservice.entity.enums.PointReason;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.repository.BadgeRepository;
import tn.cityvoice.userservice.repository.UserBadgeRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BadgeServiceImpl implements BadgeService {

    final BadgeRepository     badgeRepository;
    final UserBadgeRepository userBadgeRepository;
    final PointService        pointService;
    final NotificationClient notificationClient;

    // ── Initialiser les badges par défaut en DB ───────────
    @Override
    public void initDefaultBadges() {
        createIfNotExists("PIONEER",          "Pionnier",           "Parmi les premiers membres de CityVoice",      "🏅", BadgeCategory.SPECIAL,     50,  "#C9973E");
        createIfNotExists("PROFILE_COMPLETE",  "Profil Complet",     "Toutes les informations de profil renseignées","✅", BadgeCategory.PROFIL,       25,  "#0D9B76");
        createIfNotExists("PHOTO_ADDED",       "Belle Présence",     "Photo de profil ajoutée",                     "📸", BadgeCategory.PROFIL,       10,  "#3B82F6");
        createIfNotExists("FIRST_LOGIN",       "Bienvenue",          "Première connexion à la plateforme",           "👋", BadgeCategory.ENGAGEMENT,   5,   "#8B5CF6");
        createIfNotExists("LOYAL_MEMBER",      "Membre Fidèle",      "Membre depuis plus de 30 jours",               "🌟", BadgeCategory.COMMUNAUTE,   30,  "#E8532A");
        createIfNotExists("POINT_100",         "Centurion",          "100 points accumulés",                        "💯", BadgeCategory.ENGAGEMENT,   20,  "#C9973E");
        createIfNotExists("POINT_500",         "Vétéran",            "500 points accumulés",                        "🎖️", BadgeCategory.ENGAGEMENT,   50,  "#E8532A");
        createIfNotExists("CITY_GUARDIAN",     "Gardien de la Ville","Points les plus élevés de la plateforme",     "🛡️", BadgeCategory.SPECIAL,     100, "#0C1F3F");
        createIfNotExists("STREAK_7",           "Semaine de feu",   "7 jours de connexion consécutifs",                      "🔥", BadgeCategory.ENGAGEMENT, 30,  "#E8532A");
        createIfNotExists("STREAK_30",          "Mois de légende",  "30 jours de connexion consécutifs",                     "⚡", BadgeCategory.SPECIAL,    100, "#C9973E");
    }

    private void createIfNotExists(String code, String name, String description,
                                   String emoji, BadgeCategory cat, int pts, String color) {
        if (badgeRepository.findByCode(code).isEmpty()) {
            Badge b = new Badge();
            b.setCode(code); b.setName(name); b.setDescription(description);
            b.setEmoji(emoji); b.setCategory(cat);
            b.setPointsReward(pts); b.setColor(color);
            badgeRepository.save(b);
        }
    }

    // ── Attribuer un badge ────────────────────────────────
    @Override
    public UserBadge awardBadge(User user, String badgeCode) {
        if (hasBadge(user.getId(), badgeCode)) return null;

        return badgeRepository.findByCode(badgeCode).map(badge -> {
            UserBadge ub = new UserBadge();
            ub.setUser(user);
            ub.setBadge(badge);
            userBadgeRepository.save(ub);

            // Points seulement pour les citoyens
            if (user.getRole() == Role.CITOYEN) {
                pointService.addPoints(user, badge.getPointsReward(),
                        PointReason.BADGE_OBTENU,
                        "Badge obtenu : " + badge.getName() + " " + badge.getEmoji());
            }

            notificationClient.envoyer(
                    user.getId().toString(),
                    "BADGE",          // your mate needs to add BADGE to NotificationType enum
                    "🏅 Nouveau badge débloqué : " + badge.getName(),
                    "/user/mes-badges",
                    null
            );
            return ub;
        }).orElse(null);
    }

    // ── Vérifier et attribuer automatiquement ────────────
    @Override
    public void checkAndAwardBadges(User user) {
        // Photo ajoutée
        if (user.getPhoto() != null && !user.getPhoto().isBlank()) {
            awardBadge(user, "PHOTO_ADDED");
        }

        // Profil complet
        boolean complete = user.getNom() != null && user.getTelephone() != null
                && user.getGouvernorat() != null && user.getVille() != null
                && user.getPhoto() != null;
        if (complete) awardBadge(user, "PROFILE_COMPLETE");

        // Jalons de points
        if (user.getPoints() >= 100) awardBadge(user, "POINT_100");
        if (user.getPoints() >= 500) awardBadge(user, "POINT_500");

        // Streak badges
        if (user.getLoginStreak() >= 7)  awardBadge(user, "STREAK_7");
        if (user.getLoginStreak() >= 30) awardBadge(user, "STREAK_30");

        // Membre fidèle (30 jours)
        if (user.getDateInscription() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    user.getDateInscription(), java.time.LocalDateTime.now());
            if (days >= 30) awardBadge(user, "LOYAL_MEMBER");
        }
    }

    @Override
    public List<UserBadge> getUserBadges(UUID userId) {
        return userBadgeRepository.findByUserIdOrderByObtainedAtDesc(userId);
    }

    @Override
    public List<Badge> getAllBadges() {
        return badgeRepository.findAll();
    }

    @Override
    public boolean hasBadge(UUID userId, String badgeCode) {
        return userBadgeRepository.existsByUserIdAndBadgeCode(userId, badgeCode);
    }
}