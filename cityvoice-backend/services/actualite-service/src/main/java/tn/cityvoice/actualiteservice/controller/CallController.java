package tn.cityvoice.actualiteservice.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.actualiteservice.util.AgoraTokenUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion des appels vocaux via Agora.io
 *
 * Flux 1-1 :
 *   A → POST /api/calls/initiate         → INCOMING_CALL WebSocket → B
 *   B accepte   → frontend rejoint le canal Agora
 *   B refuse    → POST /api/calls/reject  → CALL_REJECTED WebSocket → A
 *   Fin d'appel → POST /api/calls/end     → CALL_ENDED WebSocket → autre
 *
 * Flux groupe :
 *   A → POST /api/calls/initiate-group   → INCOMING_CALL WebSocket → tous les membres
 *
 * TOKEN :
 *   Si agora.app.certificate est configuré → token dynamique généré à chaque appel (valide 1h)
 *   Sinon                                  → fallback sur agora.temp.token (token statique)
 */
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class CallController {

    private static final Logger log = LoggerFactory.getLogger(CallController.class);

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${agora.app.id}")
    private String agoraAppId;

    @Value("${agora.app.certificate:}")
    private String agoraAppCertificate;

    @Value("${agora.token.expire.seconds:3600}")
    private int tokenExpireSeconds;

    /** Utilisé seulement si le certificat n'est pas configuré */
    @Value("${agora.temp.token:}")
    private String agoraTempToken;

    // ─────────────────────────────────────────────────────────────────
    // Génération du token + canal
    // ─────────────────────────────────────────────────────────────────

    /**
     * Génère un nom de canal unique pour chaque appel (max 64 chars).
     * Format : cv_<8chars_idA>_<8chars_idB>_<timestamp5>
     */
    private String makeChannelName(String idA, String idB) {
        String a = idA.replace("-", "");
        String b = idB.replace("-", "");
        String shortA = a.length() >= 8 ? a.substring(a.length() - 8) : a;
        String shortB = b.length() >= 8 ? b.substring(b.length() - 8) : b;
        long ts = System.currentTimeMillis() % 100000L;
        return "cv_" + shortA + "_" + shortB + "_" + ts; // ≤ 30 chars
    }

    /**
     * Génère un token Agora pour le canal donné.
     *
     * Priorité :
     *   1. Token dynamique si certificat configuré ET valide
     *   2. Token statique (agora.temp.token) comme fallback
     *   3. null si aucun token disponible (mode "No Certificate" dans la console Agora)
     */
    private String generateToken(String channelName) {
        boolean certConfigured = agoraAppCertificate != null
                && !agoraAppCertificate.isBlank()
                && !agoraAppCertificate.equals("VOTRE_CERTIFICAT_ICI");

        if (certConfigured) {
            try {
                String token = AgoraTokenUtil.buildRtcToken(
                        agoraAppId, agoraAppCertificate, channelName, 0, tokenExpireSeconds);
                log.info("[Agora] ✓ Token dynamique généré pour canal '{}' (expire dans {}s)", channelName, tokenExpireSeconds);
                return token;
            } catch (Exception e) {
                log.error("[Agora] ✗ Erreur génération token dynamique — fallback sur token statique", e);
            }
        }

        // Fallback : token statique depuis application.properties
        if (agoraTempToken != null && !agoraTempToken.isBlank()) {
            log.warn("[Agora] ⚠ Utilisation du token statique (temp token) pour canal '{}'", channelName);
            return agoraTempToken;
        }

        // Aucun token disponible : mode "No Certificate" dans la console Agora
        log.warn("[Agora] ⚠ Aucun token configuré — passage en mode sans token (console Agora doit être en mode 'No Certificate')");
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Endpoints
    // ─────────────────────────────────────────────────────────────────

    /** Initier un appel 1-1 */
    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiateCall(@RequestBody Map<String, String> body) {
        String callerId    = body.get("callerId");
        String callerName  = body.get("callerName");
        String callerPhoto = body.getOrDefault("callerPhoto", "");
        String calleeId    = body.get("calleeId");

        if (callerId == null || calleeId == null) {
            return ResponseEntity.badRequest().build();
        }

        String channelName = makeChannelName(callerId, calleeId);
        String token;
        try {
            token = generateToken(channelName);
        } catch (Exception e) {
            log.error("[Agora] Impossible d'initier l'appel : {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Token Agora indisponible : " + e.getMessage()));
        }

        // Notifier le destinataire via WebSocket
        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("type",        "INCOMING_CALL");
        wsPayload.put("callerId",    callerId);
        wsPayload.put("callerName",  callerName);
        wsPayload.put("callerPhoto", callerPhoto);
        wsPayload.put("channelName", channelName);
        wsPayload.put("token",       token);
        messagingTemplate.convertAndSend("/topic/user." + calleeId, wsPayload);

        // Répondre au caller avec canal + token
        Map<String, Object> response = new HashMap<>();
        response.put("channelName", channelName);
        response.put("token",       token);
        return ResponseEntity.ok(response);
    }

    /** Initier un appel de groupe (notifie tous les membres sauf l'initiateur) */
    @PostMapping("/initiate-group")
    public ResponseEntity<Map<String, Object>> initiateGroupCall(@RequestBody Map<String, Object> body) {
        String callerId    = (String) body.get("callerId");
        String callerName  = (String) body.get("callerName");
        String callerPhoto = (String) body.getOrDefault("callerPhoto", "");
        String groupName   = (String) body.getOrDefault("groupName", "Groupe");

        @SuppressWarnings("unchecked")
        List<String> memberIds = (List<String>) body.get("memberIds");

        if (callerId == null || memberIds == null) {
            return ResponseEntity.badRequest().build();
        }

        // Canal unique pour cet appel de groupe
        String channelName = "grp_" + body.getOrDefault("groupId", "0") + "_" + (System.currentTimeMillis() % 100000L);
        // S'assurer que le nom fait ≤ 64 chars
        if (channelName.length() > 64) channelName = channelName.substring(0, 64);

        String token;
        try {
            token = generateToken(channelName);
        } catch (Exception e) {
            log.error("[Agora] Impossible d'initier l'appel de groupe : {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Token Agora indisponible : " + e.getMessage()));
        }

        // Notifier chaque membre (sauf le caller)
        for (String memberId : memberIds) {
            if (memberId.equals(callerId)) continue;
            Map<String, Object> wsPayload = new HashMap<>();
            wsPayload.put("type",        "INCOMING_CALL");
            wsPayload.put("callerId",    callerId);
            wsPayload.put("callerName",  callerName + " (groupe : " + groupName + ")");
            wsPayload.put("callerPhoto", callerPhoto);
            wsPayload.put("channelName", channelName);
            wsPayload.put("token",       token);
            wsPayload.put("isGroupCall", true);
            messagingTemplate.convertAndSend("/topic/user." + memberId, wsPayload);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("channelName", channelName);
        response.put("token",       token);
        return ResponseEntity.ok(response);
    }

    /** Refuser un appel (notifie le caller) */
    @PostMapping("/reject")
    public ResponseEntity<Map<String, String>> rejectCall(@RequestBody Map<String, String> body) {
        String callerId = body.get("callerId");
        String calleeId = body.get("calleeId");

        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("type",    "CALL_REJECTED");
        wsPayload.put("calleeId", calleeId);
        messagingTemplate.convertAndSend("/topic/user." + callerId, wsPayload);

        return ResponseEntity.ok(Map.of("message", "rejected"));
    }

    /** Terminer un appel (notifie l'autre participant) */
    @PostMapping("/end")
    public ResponseEntity<Map<String, String>> endCall(@RequestBody Map<String, String> body) {
        String otherId = body.get("otherId");

        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("type", "CALL_ENDED");
        messagingTemplate.convertAndSend("/topic/user." + otherId, wsPayload);

        return ResponseEntity.ok(Map.of("message", "ended"));
    }
}
