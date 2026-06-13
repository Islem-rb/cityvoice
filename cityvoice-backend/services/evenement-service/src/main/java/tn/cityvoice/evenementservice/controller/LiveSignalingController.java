package tn.cityvoice.evenementservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LiveSignalingController {

    private final SimpMessagingTemplate messagingTemplate;

    private final Map<Long, AtomicInteger>      spectateursCounts = new ConcurrentHashMap<>();
    private final Map<Long, Boolean>            liveStatus        = new ConcurrentHashMap<>();
    private final Map<Long, List<Map<String,Object>>> chatMessages = new ConcurrentHashMap<>();
    private final Map<Long, Map<String,Object>> pinnedMessages    = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>>        bannedUsers       = new ConcurrentHashMap<>();
    private final Map<Long, Boolean>            questionsOnly     = new ConcurrentHashMap<>();
    private final Map<Long, List<Map<String,Object>>> spectateurs = new ConcurrentHashMap<>();

    // ── Admin démarre le live ──────────────────────────
    @MessageMapping("/live/{evenementId}/start")
    public void startLive(@DestinationVariable Long evenementId,
                          @Payload Map<String, Object> payload) {
        log.info("🔴 Live démarré pour événement {}", evenementId);
        liveStatus.put(evenementId, true);
        spectateursCounts.put(evenementId, new AtomicInteger(0));
        chatMessages.put(evenementId, new ArrayList<>());
        bannedUsers.put(evenementId, new HashSet<>());
        questionsOnly.put(evenementId, false);
        spectateurs.put(evenementId, new ArrayList<>());

        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/status",
                Map.of("status", "STARTED", "evenementId", evenementId)
        );
    }

    // ── Admin arrête le live ───────────────────────────
    @MessageMapping("/live/{evenementId}/stop")
    public void stopLive(@DestinationVariable Long evenementId) {
        log.info("⏹️ Live arrêté pour événement {}", evenementId);
        liveStatus.put(evenementId, false);
        spectateursCounts.remove(evenementId);
        spectateurs.remove(evenementId);
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/status",
                Map.of("status", "STOPPED", "evenementId", evenementId)
        );
    }

    // ── Offer ──────────────────────────────────────────
    @MessageMapping("/live/{evenementId}/offer")
    public void relayOffer(@DestinationVariable Long evenementId,
                           @Payload Map<String, Object> offer) {
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/offer", offer
        );
    }

    // ── Answer ─────────────────────────────────────────
    @MessageMapping("/live/{evenementId}/answer")
    public void relayAnswer(@DestinationVariable Long evenementId,
                            @Payload Map<String, Object> answer) {
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/answer", answer
        );
    }

    // ── ICE ────────────────────────────────────────────
    @MessageMapping("/live/{evenementId}/ice")
    public void relayIce(@DestinationVariable Long evenementId,
                         @Payload Map<String, Object> candidate) {
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/ice", candidate
        );
    }

    // ── Request offer ──────────────────────────────────
    @MessageMapping("/live/{evenementId}/request-offer")
    public void requestOffer(@DestinationVariable Long evenementId) {
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/need-offer",
                Map.of("evenementId", evenementId)
        );
    }

    // ── Citoyen rejoint ────────────────────────────────
    @MessageMapping("/live/{evenementId}/join")
    public void joinLive(@DestinationVariable Long evenementId,
                         @Payload Map<String, Object> payload) {
        String userId   = (String) payload.getOrDefault("userId", "Anonyme");
        String userName = (String) payload.getOrDefault("userName", "Citoyen");

        // Vérifier si banni
        Set<String> banned = bannedUsers.getOrDefault(evenementId, new HashSet<>());
        if (banned.contains(userId)) {
            messagingTemplate.convertAndSend(
                    "/topic/live/" + evenementId + "/banned/" + userId,
                    Map.of("banned", true)
            );
            return;
        }

        int count = spectateursCounts
                .computeIfAbsent(evenementId, k -> new AtomicInteger(0))
                .incrementAndGet();

        // Ajouter à la liste spectateurs
        List<Map<String,Object>> specs = spectateurs
                .computeIfAbsent(evenementId, k -> new ArrayList<>());
        specs.removeIf(s -> userId.equals(s.get("userId")));
        specs.add(Map.of(
                "userId", userId,
                "userName", userName,
                "joinedAt", LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("HH:mm")
                )
        ));

        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/spectateurs",
                Map.of("count", count, "liste", specs)
        );

        // Notifier tout le monde
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/chat",
                Map.of(
                        "id",       UUID.randomUUID().toString(),
                        "type",     "system",
                        "message",  userName + " a rejoint le live",
                        "userName", "Système",
                        "time",     LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("HH:mm")
                        )
                )
        );

        // Envoyer l'historique chat au nouveau spectateur
        List<Map<String,Object>> hist =
                chatMessages.getOrDefault(evenementId, new ArrayList<>());
        if (!hist.isEmpty()) {
            messagingTemplate.convertAndSend(
                    "/topic/live/" + evenementId + "/chat-history",
                    Map.of("messages", hist)
            );
        }

        // Envoyer message épinglé si existe
        Map<String,Object> pinned = pinnedMessages.get(evenementId);
        if (pinned != null) {
            messagingTemplate.convertAndSend(
                    "/topic/live/" + evenementId + "/pinned",
                    pinned
            );
        }
    }

    // ── Citoyen quitte ─────────────────────────────────
    @MessageMapping("/live/{evenementId}/leave")
    public void leaveLive(@DestinationVariable Long evenementId,
                          @Payload Map<String, Object> payload) {
        String userId   = (String) payload.getOrDefault("userId", "");
        String userName = (String) payload.getOrDefault("userName", "Citoyen");

        AtomicInteger counter = spectateursCounts.get(evenementId);
        int count = counter != null ? Math.max(0, counter.decrementAndGet()) : 0;

        List<Map<String,Object>> specs =
                spectateurs.getOrDefault(evenementId, new ArrayList<>());
        specs.removeIf(s -> userId.equals(s.get("userId")));

        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/spectateurs",
                Map.of("count", count, "liste", specs)
        );

        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/chat",
                Map.of(
                        "id",       UUID.randomUUID().toString(),
                        "type",     "system",
                        "message",  userName + " a quitté le live",
                        "userName", "Système",
                        "time",     LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("HH:mm")
                        )
                )
        );
    }

    // ── Check live ─────────────────────────────────────
    @MessageMapping("/live/{evenementId}/check")
    public void checkLive(@DestinationVariable Long evenementId) {
        boolean isLive = liveStatus.getOrDefault(evenementId, false);
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/status",
                Map.of("status", isLive ? "STARTED" : "STOPPED",
                        "evenementId", evenementId)
        );
    }

    // ── 💬 Chat message ────────────────────────────────
    @MessageMapping("/live/{evenementId}/chat")
    public void sendChat(@DestinationVariable Long evenementId,
                         @Payload Map<String, Object> payload) {
        String userId = (String) payload.getOrDefault("userId", "");

        // Vérifier si banni
        if (bannedUsers.getOrDefault(evenementId, new HashSet<>()).contains(userId)) {
            return;
        }

        // Mode questions uniquement
        boolean qOnly    = questionsOnly.getOrDefault(evenementId, false);
        String  message  = (String) payload.getOrDefault("message", "");
        boolean isQuestion = message.endsWith("?");
        if (qOnly && !isQuestion &&
                !"admin".equals(payload.getOrDefault("role", ""))) {
            return;
        }

        Map<String, Object> chatMsg = new HashMap<>();
        chatMsg.put("id",       UUID.randomUUID().toString());
        chatMsg.put("type",     "message");
        chatMsg.put("message",  message);
        chatMsg.put("userId",   userId);
        chatMsg.put("userName", payload.getOrDefault("userName", "Citoyen"));
        chatMsg.put("role",     payload.getOrDefault("role", "citoyen"));
        chatMsg.put("time",     LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm")
        ));

        // Sauvegarder dans l'historique
        List<Map<String,Object>> hist =
                chatMessages.computeIfAbsent(evenementId, k -> new ArrayList<>());
        hist.add(chatMsg);
        if (hist.size() > 100) hist.remove(0); // Max 100 messages

        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/chat", chatMsg
        );
    }

    // ── 📌 Épingler un message ─────────────────────────
    @MessageMapping("/live/{evenementId}/pin")
    public void pinMessage(@DestinationVariable Long evenementId,
                           @Payload Map<String, Object> payload) {
        pinnedMessages.put(evenementId, payload);
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/pinned", payload
        );
    }

    // ── 🗑️ Supprimer un message ────────────────────────
    @MessageMapping("/live/{evenementId}/delete-message")
    public void deleteMessage(@DestinationVariable Long evenementId,
                              @Payload Map<String, Object> payload) {
        String msgId = (String) payload.get("messageId");
        List<Map<String,Object>> hist =
                chatMessages.getOrDefault(evenementId, new ArrayList<>());
        hist.removeIf(m -> msgId.equals(m.get("id")));
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/message-deleted",
                Map.of("messageId", msgId)
        );
    }

    // ── 🚫 Bannir un spectateur ────────────────────────
    @MessageMapping("/live/{evenementId}/ban")
    public void banUser(@DestinationVariable Long evenementId,
                        @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        bannedUsers.computeIfAbsent(evenementId, k -> new HashSet<>()).add(userId);

        // Notifier le banni
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/banned/" + userId,
                Map.of("banned", true, "reason", "Banni par l'administrateur")
        );

        // Retirer de la liste spectateurs
        List<Map<String,Object>> specs =
                spectateurs.getOrDefault(evenementId, new ArrayList<>());
        specs.removeIf(s -> userId.equals(s.get("userId")));

        AtomicInteger counter = spectateursCounts.get(evenementId);
        int count = counter != null ? Math.max(0, counter.decrementAndGet()) : 0;
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/spectateurs",
                Map.of("count", count, "liste", specs)
        );

        log.info("🚫 Utilisateur {} banni du live {}", userId, evenementId);
    }

    // ── ❓ Mode questions uniquement ───────────────────
    @MessageMapping("/live/{evenementId}/questions-only")
    public void toggleQuestionsOnly(@DestinationVariable Long evenementId,
                                    @Payload Map<String, Object> payload) {
        boolean enabled = (boolean) payload.getOrDefault("enabled", false);
        questionsOnly.put(evenementId, enabled);
        messagingTemplate.convertAndSend(
                "/topic/live/" + evenementId + "/mode",
                Map.of("questionsOnly", enabled)
        );
        log.info("❓ Mode questions uniquement: {} pour événement {}", enabled, evenementId);
    }
}