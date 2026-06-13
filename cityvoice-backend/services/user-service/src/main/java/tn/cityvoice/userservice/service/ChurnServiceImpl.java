package tn.cityvoice.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.repository.UserBadgeRepository;
import tn.cityvoice.userservice.repository.UserRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChurnServiceImpl implements ChurnService {

    final UserRepository     userRepository;
    final UserBadgeRepository userBadgeRepository;
    final RestTemplate       restTemplate;
    final UserServiceImpl    userService;

    @Value("${services.ml.churn.url:http://localhost:8001}")
    private String mlChurnUrl;

    @Override
    @SuppressWarnings("unchecked")
    public ChurnPrediction predict(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Construire les features
        Map<String, Object> features = buildFeatures(user);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(features, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    mlChurnUrl + "/predict",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getBody() == null) {
                return fallbackPrediction(user);
            }

            Map<String, Object> body = response.getBody();

            // Parser les retention actions
            List<RetentionAction> actions = new ArrayList<>();
            List<Map<String, Object>> rawActions =
                    (List<Map<String, Object>>) body.get("retention_actions");
            if (rawActions != null) {
                rawActions.forEach(a -> actions.add(new RetentionAction(
                        (String) a.get("action"),
                        (String) a.get("priority"),
                        (String) a.get("expected_impact")
                )));
            }

            return new ChurnPrediction(
                    userId.toString(),
                    ((Number) body.getOrDefault("churn_probability", 0.0)).doubleValue(),
                    (String) body.getOrDefault("risk_level", "LOW"),
                    body.get("days_until_predicted_churn") != null
                            ? ((Number) body.get("days_until_predicted_churn")).intValue()
                            : null,
                    (List<String>) body.getOrDefault("risk_factors", List.of()),
                    actions,
                    ((Number) body.getOrDefault("model_confidence", 0.7)).doubleValue()
            );

        } catch (Exception e) {
            System.err.println("⚠️ Churn ML service unavailable: " + e.getMessage());
            return fallbackPrediction(user);
        }
    }

    private Map<String, Object> buildFeatures(User user) {
        long daysSinceReg = user.getDateInscription() != null
                ? ChronoUnit.DAYS.between(
                user.getDateInscription().toLocalDate(), LocalDate.now())
                : 0;

        long daysSinceLogin = user.getLastSeenAt() != null
                ? ChronoUnit.DAYS.between(
                user.getLastSeenAt().toLocalDate(), LocalDate.now())
                : daysSinceReg;

        int badgeCount = (int) userBadgeRepository
                .findByUserIdOrderByObtainedAtDesc(user.getId()).size();

        double profileCompleteness = calculateProfileCompleteness(user);
        int civicIndex = userService.getCivicIndex(user);

        Map<String, Object> features = new HashMap<>();
        features.put("user_id",                user.getId().toString());
        features.put("days_since_registration", (int) daysSinceReg);
        features.put("days_since_last_login",   (int) daysSinceLogin);
        features.put("login_streak",            user.getLoginStreak());
        features.put("total_points",            user.getPoints());
        features.put("monthly_points",          0); // calculé séparément si besoin
        features.put("profile_completeness",    profileCompleteness);
        features.put("email_verified",          user.isEmailVerified());
        features.put("has_photo",               user.getPhoto() != null);
        features.put("trust_level",             user.getTrustLevel() != null
                ? user.getTrustLevel().name() : "NOUVEAU");
        features.put("login_count_30d",         Math.min(30, user.getLoginStreak()));
        features.put("points_last_7d",          0);
        features.put("civic_index",             Math.max(0, civicIndex));
        features.put("governorate",             user.getGouvernorat());

        return features;
    }

    private double calculateProfileCompleteness(User user) {
        int score = 0;
        if (user.getNom()         != null && !user.getNom().isBlank())         score += 20;
        if (user.getTelephone()   != null && !user.getTelephone().isBlank())   score += 20;
        if (user.getGouvernorat() != null && !user.getGouvernorat().isBlank()) score += 20;
        if (user.getVille()       != null && !user.getVille().isBlank())       score += 20;
        if (user.getPhoto()       != null && !user.getPhoto().isBlank())       score += 20;
        return score;
    }

    // Heuristique si ML indisponible
    private ChurnPrediction fallbackPrediction(User user) {
        double score = 0.0;
        List<String> factors = new ArrayList<>();

        long daysSinceLogin = user.getLastSeenAt() != null
                ? ChronoUnit.DAYS.between(user.getLastSeenAt().toLocalDate(), LocalDate.now())
                : 30;

        if (daysSinceLogin >= 30)  { score += 0.4; factors.add("Inactif depuis " + daysSinceLogin + " jours"); }
        else if (daysSinceLogin >= 14) { score += 0.25; factors.add("Pas de connexion depuis 2 semaines"); }
        if (user.getLoginStreak() == 0)         { score += 0.1; factors.add("Streak interrompu"); }
        if (!user.isEmailVerified())             { score += 0.1; factors.add("Email non vérifié"); }
        if (user.getPhoto() == null)             { score += 0.1; factors.add("Pas de photo de profil"); }
        if (user.getPoints() < 50)               { score += 0.1; factors.add("Faible engagement (points < 50)"); }

        double prob = Math.min(0.95, score);
        String level = prob >= 0.8 ? "CRITICAL" : prob >= 0.6 ? "HIGH"
                : prob >= 0.4 ? "MEDIUM" : "LOW";

        return new ChurnPrediction(
                user.getId().toString(), prob, level,
                prob > 0.3 ? (int)((1 - prob) * 30) : null,
                factors, List.of(), 0.6
        );
    }
}