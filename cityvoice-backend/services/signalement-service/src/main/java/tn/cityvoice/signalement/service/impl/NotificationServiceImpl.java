package tn.cityvoice.signalement.service.impl;

import tn.cityvoice.signalement.entity.Notification;
import tn.cityvoice.signalement.enums.NotificationType;
import tn.cityvoice.signalement.repository.NotificationRepository;
import tn.cityvoice.signalement.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationServiceImpl implements INotificationService {

    private final NotificationRepository repo;
    private final SimpMessagingTemplate  ws;    // WebSocket STOMP

    /**
     * Crée la notification en base PUIS la pousse en temps réel
     * sur le topic WebSocket personnel de l'utilisateur :
     *   /topic/notifications/{destinataireId}
     */
    @Override
    public Notification envoyer(String destinataireId, NotificationType type,
                                String message, String lien, Long entiteId) {

        Notification notif = Notification.builder()
            .destinataireId(destinataireId)
            .type(type)
            .message(message)
            .lien(lien)
            .entiteId(entiteId)
            .lu(false)
            .build();

        Notification saved = repo.save(notif);
        log.info("[NOTIF] → {} | type={} | msg={}", destinataireId, type, message);

        // Push WebSocket (ignoré si pas de connexion active)
        try {
            ws.convertAndSend("/topic/notifications/" + destinataireId, saved);
        } catch (Exception e) {
            log.warn("[NOTIF WS] Echec push WebSocket pour {} : {}", destinataireId, e.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getByUser(String userId) {
        return repo.findByDestinataireIdOrderByDateCreationDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getNonLues(String userId) {
        return repo.findByDestinataireIdAndLuFalseOrderByDateCreationDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countNonLues(String userId) {
        return repo.countByDestinataireIdAndLuFalse(userId);
    }

    @Override
    public Notification marquerLue(Long notifId) {
        Notification n = repo.findById(notifId)
            .orElseThrow(() -> new RuntimeException("Notification introuvable: " + notifId));
        n.setLu(true);
        return repo.save(n);
    }

    @Override
    public void marquerToutesLues(String userId) {
        repo.markAllReadByUser(userId);
        log.info("[NOTIF] Toutes notifs marquées lues pour {}", userId);
    }
}
