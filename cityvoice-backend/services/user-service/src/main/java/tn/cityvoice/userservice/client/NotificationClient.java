package tn.cityvoice.userservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.Map;

@Component
public class NotificationClient {

    private final RestTemplate restTemplate;

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    public NotificationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void envoyer(String destinataireId, String type,
                        String message, String lien, Long entiteId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "destinataireId", destinataireId,
                    "type",           type,
                    "message",        message,
                    "lien",           lien != null ? lien : "",
                    "entiteId",       entiteId != null ? entiteId : 0
            );

            restTemplate.postForEntity(
                    notificationServiceUrl + "/api/v1/notifications/interne",
                    new HttpEntity<>(body, headers),
                    Void.class
            );
        } catch (Exception e) {
            // Never block user flow if notification fails
            System.err.println("[NOTIF] Failed to send: " + e.getMessage());
        }
    }
}