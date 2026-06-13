package tn.cityvoice.actualiteservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.actualiteservice.dto.NotificationDTO;
import tn.cityvoice.actualiteservice.entity.Notification;
import tn.cityvoice.actualiteservice.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final SimpMessagingTemplate messagingTemplate;

    // ============================================================
    // PUSH + PERSIST
    // ============================================================

    /**
     * Crée une notification en base et la pousse via WebSocket au destinataire.
     */
    public void send(String recipientId,
                     String actorId,
                     String actorName,
                     String actorPhoto,
                     String type,
                     String message,
                     Long postId) {

        // Ne pas notifier si l'auteur est l'acteur (auto-réaction)
        if (recipientId == null || actorId == null) {
            log.warn("[Notif] recipientId ou actorId null — notification ignorée");
            return;
        }
        if (recipientId.equals(actorId)) {
            log.debug("[Notif] Auto-action ignorée pour userId={}", actorId);
            return;
        }

        log.info("[Notif] Création notification type={} de {} → {}: {}", type, actorId, recipientId, message);

        Notification notif = new Notification();
        notif.setRecipientId(recipientId);
        notif.setActorId(actorId);
        notif.setActorName(actorName);
        notif.setActorPhoto(actorPhoto);
        notif.setType(type);
        notif.setMessage(message);
        notif.setPostId(postId);
        notif.setRead(false);
        notif.setCreatedAt(LocalDateTime.now());

        Notification saved = notificationRepo.save(notif);
        log.info("[Notif] ✅ Sauvegardée en BDD id={}", saved.getId());

        // Push WebSocket → /topic/user.{recipientId}
        messagingTemplate.convertAndSend(
                "/topic/user." + recipientId,
                toDTO(saved)
        );
        log.info("[Notif] ✅ Poussée via WebSocket vers /topic/user.{}", recipientId);
    }

    // ============================================================
    // QUERIES
    // ============================================================

    public List<NotificationDTO> getForUser(String userId) {
        return notificationRepo
                .findByRecipientIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public long countUnread(String userId) {
        return notificationRepo.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(String userId) {
        notificationRepo.markAllRead(userId);
    }

    @Transactional
    public void markOneRead(Long notifId) {
        notificationRepo.findById(notifId).ifPresent(n -> {
            n.setRead(true);
            notificationRepo.save(n);
        });
    }

    // ============================================================
    // MAPPER
    // ============================================================

    private NotificationDTO toDTO(Notification n) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(n.getId());
        dto.setRecipientId(n.getRecipientId());
        dto.setActorId(n.getActorId());
        dto.setActorName(n.getActorName());
        dto.setActorPhoto(n.getActorPhoto());
        dto.setType(n.getType());
        dto.setMessage(n.getMessage());
        dto.setPostId(n.getPostId());
        dto.setRead(n.isRead());
        dto.setCreatedAt(n.getCreatedAt());
        return dto;
    }
}
