package tn.cityvoice.personnelservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.cityvoice.personnelservice.entity.Notification;
import tn.cityvoice.personnelservice.repository.NotificationRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate  messagingTemplate;

    public void sendNotification(UUID receiverId, UUID senderId,
                                 String title, String message, String type,
                                 UUID cvId, String fonction) {
        // 1. Persister
        Notification notif = new Notification();
        notif.setReceiverId(receiverId);
        notif.setSenderId(senderId);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setType(type);
        notif.setRead(false);
        notif.setCvId(cvId);           // ← null pour les notifs non-quiz
        notif.setFonction(fonction);   // ← null pour les notifs non-quiz
        notificationRepository.save(notif);

        // 2. Push WebSocket en temps réel
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id",         notif.getId().toString());
        payload.put("receiverId", receiverId.toString());
        payload.put("senderId",   senderId.toString());
        payload.put("title",      title);
        payload.put("message",    message);
        payload.put("type",       type);
        payload.put("read",       false);
        payload.put("createdAt",  notif.getCreatedAt().toString());
        if (cvId     != null) payload.put("cvId",     cvId.toString());
        if (fonction != null) payload.put("fonction", fonction);

        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/queue/notifications",
                payload
        );

        log.info(">>> [Notif] Envoyée → receiverId={} type={} cvId={}", receiverId, type, cvId);
    }

    public List<Notification> getNotifications(UUID userId) {
        return notificationRepository.findByReceiverIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(UUID userId) {
        return notificationRepository.findByReceiverIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    public long countUnread(UUID userId) {
        return notificationRepository.countByReceiverIdAndReadFalse(userId);
    }

    public void markAllAsRead(UUID userId) {
        List<Notification> unread =
                notificationRepository.findByReceiverIdAndReadFalseOrderByCreatedAtDesc(userId);

        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
    public void markAsRead(UUID notifId) {
        Notification notif = notificationRepository.findById(notifId)
                .orElseThrow(() -> new RuntimeException("Notification non trouvée"));
        notif.setRead(true);
        notificationRepository.save(notif);
    }

}