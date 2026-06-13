package tn.cityvoice.actualiteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.actualiteservice.dto.NotificationDTO;
import tn.cityvoice.actualiteservice.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class NotificationController {

    private final NotificationService notifService;

    /** Récupère toutes les notifications d'un utilisateur */
    @GetMapping("/{userId}")
    public ResponseEntity<List<NotificationDTO>> getNotifications(
            @PathVariable("userId") String userId) {
        return ResponseEntity.ok(notifService.getForUser(userId));
    }

    /** Nombre de notifications non lues */
    @GetMapping("/{userId}/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @PathVariable("userId") String userId) {
        return ResponseEntity.ok(Map.of("count", notifService.countUnread(userId)));
    }

    /** Marquer toutes les notifications comme lues */
    @PutMapping("/{userId}/mark-all-read")
    public ResponseEntity<Void> markAllRead(
            @PathVariable("userId") String userId) {
        notifService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    /** Marquer une notification comme lue */
    @PutMapping("/read/{notifId}")
    public ResponseEntity<Void> markOneRead(
            @PathVariable("notifId") Long notifId) {
        notifService.markOneRead(notifId);
        return ResponseEntity.ok().build();
    }
}
