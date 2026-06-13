package tn.cityvoice.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.PointReason;
import tn.cityvoice.userservice.repository.UserRepository;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class StreakServiceImpl implements StreakService {

    final UserRepository userRepository;
    final PointService   pointService;
    final BadgeService   badgeService;

    @Override
    public void updateStreak(User user) {
        LocalDate today     = LocalDate.now();
        LocalDate lastLogin = user.getLastLoginDate();

        if (lastLogin == null) {
            // Première connexion
            user.setLoginStreak(1);

        } else if (lastLogin.equals(today)) {
            // Déjà connecté aujourd'hui → rien
            return;

        } else if (lastLogin.equals(today.minusDays(1))) {
            // Connexion consécutive
            user.setLoginStreak(user.getLoginStreak() + 1);

        } else {
            // Streak cassé
            user.setLoginStreak(1);
        }

        user.setLastLoginDate(today);
        userRepository.save(user);

        // Récompenses streak
        int streak = user.getLoginStreak();

        if (streak == 3) {
            pointService.addPoints(user, 15, PointReason.BADGE_OBTENU,
                    "3 jours consécutifs 🔥");
        }
        if (streak == 7) {
            pointService.addPoints(user, 30, PointReason.BADGE_OBTENU,
                    "7 jours consécutifs 🔥");
            badgeService.awardBadge(user, "STREAK_7");
        }
        if (streak == 30) {
            pointService.addPoints(user, 100, PointReason.BADGE_OBTENU,
                    "30 jours consécutifs 🏆");
            badgeService.awardBadge(user, "STREAK_30");
        }
    }
}