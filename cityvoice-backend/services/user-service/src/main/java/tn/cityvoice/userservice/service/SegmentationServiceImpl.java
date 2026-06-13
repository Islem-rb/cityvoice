package tn.cityvoice.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.repository.UserRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SegmentationServiceImpl implements SegmentationService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final UserService userService;

    @Value("${services.ml.churn.url:http://localhost:8001}")
    private String mlServiceUrl;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> segment(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> features = buildFeatures(user);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(features, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    mlServiceUrl + "/segment",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            return response.getBody() != null ? response.getBody() : Map.of("error", "Empty response");

        } catch (Exception e) {
            System.err.println("[SEGMENT] ML service error: " + e.getMessage());
            return Map.of("error", "Service unavailable", "message", e.getMessage());
        }
    }

    private Map<String, Object> buildFeatures(User user) {
        long daysSinceReg = user.getDateInscription() != null
                ? ChronoUnit.DAYS.between(user.getDateInscription().toLocalDate(), LocalDate.now()) : 0;
        long daysSinceLogin = user.getLastSeenAt() != null
                ? ChronoUnit.DAYS.between(user.getLastSeenAt().toLocalDate(), LocalDate.now()) : daysSinceReg;
        int civicIndex = userService.getCivicIndex(user);

        Map<String, Object> f = new HashMap<>();
        f.put("user_id", user.getId().toString());
        f.put("days_since_registration", (int) daysSinceReg);
        f.put("days_since_last_login", (int) daysSinceLogin);
        f.put("login_streak", user.getLoginStreak());
        f.put("total_points", user.getPoints());
        f.put("monthly_points", 0);
        f.put("profile_completeness", calculateCompleteness(user));
        f.put("email_verified", user.isEmailVerified());
        f.put("has_photo", user.getPhoto() != null);
        f.put("trust_level", user.getTrustLevel() != null ? user.getTrustLevel().name() : "NOUVEAU");
        f.put("login_count_30d", Math.min(30, user.getLoginStreak()));
        f.put("points_last_7d", 0);
        f.put("civic_index", Math.max(0, civicIndex));
        f.put("governorate", user.getGouvernorat());
        return f;
    }

    private double calculateCompleteness(User user) {
        int s = 0;
        if (user.getNom() != null && !user.getNom().isBlank()) s += 20;
        if (user.getTelephone() != null && !user.getTelephone().isBlank()) s += 20;
        if (user.getGouvernorat() != null && !user.getGouvernorat().isBlank()) s += 20;
        if (user.getVille() != null && !user.getVille().isBlank()) s += 20;
        if (user.getPhoto() != null && !user.getPhoto().isBlank()) s += 20;
        return s;
    }
}