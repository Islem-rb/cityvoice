package tn.cityvoice.personnelservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.personnelservice.entity.Notification;
import tn.cityvoice.personnelservice.service.NotificationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/personnel/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getAll(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnread(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId));
    }

    @GetMapping("/user/{userId}/unread/count")
    public ResponseEntity<Map<String, Long>> countUnread(@PathVariable("userId") UUID userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(userId)));
    }



    @PatchMapping("/user/{userId}/read-all")
    public ResponseEntity<Void> markAllRead(@PathVariable("userId") UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{notifId}/read")
    public ResponseEntity<Void> markRead(@PathVariable("notifId") UUID notifId) {
        notificationService.markAsRead(notifId);
        return ResponseEntity.noContent().build();
    }
}