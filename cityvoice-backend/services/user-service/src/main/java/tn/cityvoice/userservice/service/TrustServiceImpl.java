package tn.cityvoice.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.userservice.client.NotificationClient;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.TrustLevel;
import tn.cityvoice.userservice.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class TrustServiceImpl implements TrustService {

    final UserRepository userRepository;
    final NotificationClient notificationClient;

    @Override
    public TrustLevel calculate(int points) {
        if (points >= 1000) return TrustLevel.AMBASSADEUR;
        if (points >= 500)  return TrustLevel.VETERAN;
        if (points >= 200)  return TrustLevel.HABITUE;
        if (points >= 50)   return TrustLevel.MEMBRE;
        return TrustLevel.NOUVEAU;
    }

    @Override
    public boolean updateIfChanged(User user) {
        TrustLevel newLevel = calculate(user.getPoints());
        if (newLevel != user.getTrustLevel()) {
            user.setTrustLevel(newLevel);
            userRepository.save(user);
            // 🔔 Notify
            notificationClient.envoyer(
                    user.getId().toString(),
                    "INFO",
                    "🎉 Nouveau niveau atteint : " + getTrustLevelLabel(newLevel),
                    "/user/profil",
                    null
            );
            return true; // niveau changé
        }
        return false;
    }

    private String getTrustLevelLabel(TrustLevel level) {
        return switch (level) {
            case NOUVEAU     -> "Nouveau 🌱";
            case MEMBRE      -> "Membre ⭐";
            case HABITUE     -> "Habitué 🔥";
            case VETERAN     -> "Vétéran 💎";
            case AMBASSADEUR -> "Ambassadeur 👑";
        };
    }
}