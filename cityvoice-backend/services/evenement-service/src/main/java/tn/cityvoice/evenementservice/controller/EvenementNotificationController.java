package tn.cityvoice.evenementservice.controller;

import tn.cityvoice.evenementservice.entity.EvenementNotification;
import tn.cityvoice.evenementservice.service.EvenementNotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:4200")
public class EvenementNotificationController {

    private final EvenementNotificationService service;

    public EvenementNotificationController(EvenementNotificationService service) {
        this.service = service;
    }

    // ── SSE stream ────────────────────────────────────
    @GetMapping(value = "/stream/{destinataireId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String destinataireId) {
        return service.abonner(destinataireId);
    }

    // ── GET toutes les notifs ─────────────────────────
    @GetMapping("/{destinataireId}")
    public ResponseEntity<List<EvenementNotification>> getNotifications(
            @PathVariable String destinataireId) {
        return ResponseEntity.ok(service.getNotifications(destinataireId));
    }

    // ── GET nombre non lues ───────────────────────────
    @GetMapping("/{destinataireId}/count")
    public ResponseEntity<Long> compterNonLues(
            @PathVariable String destinataireId) {
        return ResponseEntity.ok(service.compterNonLues(destinataireId));
    }

    // ── Marquer une notif comme lue ───────────────────
    @PutMapping("/{id}/lire")
    public ResponseEntity<Void> marquerLue(@PathVariable Long id) {
        service.marquerLue(id);
        return ResponseEntity.ok().build();
    }

    // ── Marquer toutes comme lues ─────────────────────
    @PutMapping("/{destinataireId}/tout-lire")
    public ResponseEntity<Void> marquerToutesLues(
            @PathVariable String destinataireId) {
        service.marquerToutesLues(destinataireId);
        return ResponseEntity.ok().build();
    }
}
