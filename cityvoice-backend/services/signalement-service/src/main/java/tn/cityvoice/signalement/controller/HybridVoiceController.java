package tn.cityvoice.signalement.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.cityvoice.signalement.dto.HybridVoiceSessionResponse;
import tn.cityvoice.signalement.dto.JamiMessageRequest;
import tn.cityvoice.signalement.dto.JamiSignalementResponse;
import tn.cityvoice.signalement.dto.VoiceUploadRequest;
import tn.cityvoice.signalement.service.impl.HybridVoiceService;

import java.util.Map;

/**
 * Contrôleur pour le système vocal hybride (Web + Jami).
 * Traite les appels depuis:
 * - WebRTC (navigateur Angular)
 * - Jami (app mobile/desktop)
 */
@RestController
@RequestMapping("/api/v1/hybrid-voice")
@RequiredArgsConstructor
@Slf4j
public class HybridVoiceController {

    private final HybridVoiceService hybridVoiceService;

    /**
     * Webhook pour recevoir les audios enregistrés.
     * POST /api/v1/hybrid-voice/upload
     *
     * L'audio est envoyé dans le body JSON (pas en query param)
     * pour éviter la limite de taille des URLs (414 Request-URI Too Long).
     *
     * @param request DTO contenant sessionId, source, step, audioBase64
     * @return sessionId pour tracking
     */
    @PostMapping(value = "/upload",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> uploadAudio(@RequestBody VoiceUploadRequest request) {
        try {
            String returnedSessionId = hybridVoiceService.createOrUpdateSession(
                request.getSessionId(),
                request.getSource(),
                request.getStep(),
                request.getAudioBase64(),
                request.getUserId()
            );
            log.info("[HYBRID] Audio reçu: session={}, source={}, step={}", returnedSessionId, request.getSource(), request.getStep());
            return ResponseEntity.ok(Map.of("sessionId", returnedSessionId, "status", "received"));
        } catch (Exception e) {
            log.error("[HYBRID] Erreur upload audio: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Récupère l'état d'une session.
     * GET /api/v1/hybrid-voice/session/{sessionId}
     *
     * @param sessionId ID de session
     * @return Détails de la session
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<HybridVoiceSessionResponse> getSession(@PathVariable String sessionId) {
        HybridVoiceSessionResponse response = hybridVoiceService.getSession(sessionId);
        if (response.getErrorMessage() != null && response.getErrorMessage().contains("introuvable")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Webhook Jami: reçoit description + localisation texte depuis le bot Node.js.
     * POST /api/v1/hybrid-voice/jami-message
     *
     * Le bot Jami conduit la conversation en messages texte,
     * puis envoie ici la description et la localisation pour créer le signalement.
     */
    @PostMapping(value = "/jami-message",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JamiSignalementResponse> createFromJamiMessage(@RequestBody JamiMessageRequest request) {
        try {
            log.info("[JAMI] Message reçu: session={}, from={}", request.getSessionId(), request.getContactUri());
            JamiSignalementResponse response = hybridVoiceService.processJamiMessage(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[JAMI] Erreur traitement message: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Récupère l'audio enregistré pour relecture (admin).
     * GET /api/v1/hybrid-voice/audio/{sessionId}/{step}
     *
     * @param sessionId ID de session
     * @param step "description" ou "location"
     * @return Fichier audio WAV
     */
    @GetMapping("/audio/{sessionId}/{step}")
    public ResponseEntity<byte[]> getAudio(
        @PathVariable String sessionId,
        @PathVariable String step
    ) {
        try {
            byte[] audioData = hybridVoiceService.getAudio(sessionId, step);
            return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/webm"))
                .header("Content-Disposition", "inline; filename=\"voice-" + step + ".webm\"")
                .header("Access-Control-Allow-Origin", "*")
                .body(audioData);
        } catch (Exception e) {
            log.error("[HYBRID] Erreur lecture audio: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
