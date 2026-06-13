package tn.cityvoice.signalement.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Client HTTP léger pour récupérer le téléphone et les préférences
 * de notification d'un utilisateur depuis le user-service.
 *
 * On utilise RestTemplate (pas Feign) pour éviter les dépendances
 * circulaires et rester découplé.
 */
@Component
@Slf4j
public class UserNotifClient {

    @Value("${user.service.url:http://localhost:8081}")
    private String userServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Retourne les infos de notification d'un utilisateur.
     * Retourne null si le user-service est indisponible.
     */
    public UserNotifInfo getUserNotifInfo(String userId) {
        try {
            String url = userServiceUrl + "/api/users/" + userId;
            ResponseEntity<UserNotifInfo> resp =
                restTemplate.getForEntity(url, UserNotifInfo.class);
            return resp.getBody();
        } catch (Exception e) {
            log.warn("[UserNotifClient] Impossible de récupérer l'utilisateur {} : {}", userId, e.getMessage());
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserNotifInfo {
        private String  id;
        private String  nom;
        private String  telephone;
        private boolean whatsappNotifs;
        private boolean smsNotifs;
        private String  callmebotApiKey;
    }
}
