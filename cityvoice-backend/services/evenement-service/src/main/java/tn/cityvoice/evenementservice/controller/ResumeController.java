package tn.cityvoice.evenementservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.repository.EvenementRepository;
import tn.cityvoice.evenementservice.service.ResumeService;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService      resumeService;
    private final EvenementRepository evenementRepository;

    // ── Post social media ──────────────────────────
    @GetMapping("/{id}/social")
    public ResponseEntity<Map<String, String>> postSocial(
            @PathVariable Long id,
            @RequestParam(defaultValue = "facebook") String plateforme) {
        Evenement ev = evenementRepository.findById(id).orElseThrow();
        String text  = resumeService.genererTextePartage(ev, plateforme);
        return ResponseEntity.ok(Map.of(
                "post",       text,
                "plateforme", plateforme
        ));
    }

    // ── Résumé court ───────────────────────────────
    @GetMapping("/{id}/court")
    public ResponseEntity<Map<String, String>> resumeCourt(
            @PathVariable Long id) {
        Evenement ev = evenementRepository.findById(id).orElseThrow();
        String text  = resumeService.genererResumeCourt(ev);
        return ResponseEntity.ok(Map.of("resume", text));
    }

    // ── Notification ───────────────────────────────
    @GetMapping("/{id}/notification")
    public ResponseEntity<Map<String, String>> notification(
            @PathVariable Long id) {
        Evenement ev = evenementRepository.findById(id).orElseThrow();
        String text  = resumeService.genererNotification(ev);
        return ResponseEntity.ok(Map.of("notification", text));
    }

    // ── Email invitation ───────────────────────────
    @GetMapping("/{id}/email")
    public ResponseEntity<Map<String, String>> emailInvitation(
            @PathVariable Long id) {
        Evenement ev = evenementRepository.findById(id).orElseThrow();
        String text  = resumeService.genererEmailInvitation(ev);
        return ResponseEntity.ok(Map.of("email", text));
    }
}