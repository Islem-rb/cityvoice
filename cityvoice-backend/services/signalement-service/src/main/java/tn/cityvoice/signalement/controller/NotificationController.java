package tn.cityvoice.signalement.controller;

import tn.cityvoice.signalement.entity.Notification;
import tn.cityvoice.signalement.enums.NotificationType;
import tn.cityvoice.signalement.service.INotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints pour les notifications.
 * Utilisés en fallback (polling) quand le WebSocket n'est pas disponible.
 *
 * Base URL (via Gateway) : /api/v1/notifications
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService svc;

    /** Toutes les notifs de l'utilisateur (polling) */
    @GetMapping
    public ResponseEntity<List<Notification>> getMes(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(svc.getByUser(userId));
    }

    /** Seulement les non lues */
    @GetMapping("/non-lues")
    public ResponseEntity<List<Notification>> getNonLues(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(svc.getNonLues(userId));
    }

    /** Compteur non lues (pour badge cloche) */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(Map.of("count", svc.countNonLues(userId)));
    }

    /** Marquer une notif comme lue */
    @PatchMapping("/{id}/lue")
    public ResponseEntity<Notification> marquerLue(@PathVariable Long id) {
        return ResponseEntity.ok(svc.marquerLue(id));
    }

    /** Marquer toutes comme lues */
    @PatchMapping("/tout-lire")
    public ResponseEntity<Void> marquerToutesLues(
            @RequestHeader("X-User-Id") String userId) {
        svc.marquerToutesLues(userId);
        return ResponseEntity.noContent().build();
    }

    /** Islem **/
    @PostMapping("/interne")
    public ResponseEntity<Void> envoyerInterne(@RequestBody Map<String, Object> body) {
        svc.envoyer(
                (String) body.get("destinataireId"),
                NotificationType.valueOf((String) body.get("type")),
                (String) body.get("message"),
                (String) body.get("lien"),
                body.get("entiteId") != null ? Long.valueOf(body.get("entiteId").toString()) : null
        );
        return ResponseEntity.ok().build();
    }
}
