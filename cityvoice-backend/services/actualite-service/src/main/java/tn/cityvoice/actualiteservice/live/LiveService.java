package tn.cityvoice.actualiteservice.live;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LiveService — gère les lives via LiveKit Cloud.
 *
 * LiveKit offre 50 000 min/mois gratuites sans carte bancaire, contrairement
 * à Daily.co qui en exigeait une. Les rooms LiveKit sont créées automatiquement
 * à la première connexion WebSocket — on génère seulement des JWT côté serveur.
 *
 * Chaque participant reçoit un token signé HS256 contenant :
 *   - iss : la clé API LiveKit (APIxxxx)
 *   - sub : identifiant unique du participant
 *   - name : nom d'affichage
 *   - video.room : nom de la room
 *   - video.roomJoin : true
 *   - video.canPublish : true (streamer) / false (viewer)
 *   - video.canSubscribe : true (toujours)
 *   - exp / nbf : validité du token
 */
@Service
public class LiveService {

    private static final Logger log = LoggerFactory.getLogger(LiveService.class);

    @Value("${livekit.api.key:}")
    private String livekitApiKey;

    @Value("${livekit.api.secret:}")
    private String livekitApiSecret;

    @Value("${livekit.ws.url:}")
    private String livekitWsUrl;

    @Value("${livekit.token.ttl.seconds:14400}")
    private long tokenTtlSeconds;

    /** Rooms actives gérées par cette instance (thread-safe). */
    private final Map<String, LiveRoomDto> activeRooms = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;

    public LiveService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Crée un live : génère un nom de room unique + un token streamer,
     * enregistre la room en mémoire et broadcast l'événement "LIVE_STARTED".
     * Aucun appel API externe — LiveKit crée la room automatiquement à la connexion.
     */
    public LiveRoomDto createRoom(String username, String title, String userId) {
        validateConfig();

        String roomName = "cv-" + UUID.randomUUID().toString().substring(0, 8);
        String identity = (userId != null && !userId.isBlank())
            ? "streamer-" + userId
            : "streamer-" + UUID.randomUUID().toString().substring(0, 8);

        String streamerToken = generateToken(roomName, identity, username, true);

        LiveRoomDto dto = new LiveRoomDto();
        dto.setRoomName(roomName);
        dto.setRoomUrl(livekitWsUrl);   // côté front, c'est l'URL WebSocket LiveKit
        dto.setWsUrl(livekitWsUrl);
        dto.setToken(streamerToken);
        dto.setStreamerUsername(username);
        dto.setStreamerUserId(userId);
        dto.setTitle(title != null && !title.isBlank() ? title : "Live de " + username);
        dto.setStartedAt(Instant.now().toString());
        dto.setViewerCount(0);

        activeRooms.put(roomName, dto);
        log.info("[Live] Room LiveKit créée : {} (streamer={})", roomName, username);

        // On ne diffuse PAS le token streamer (sensible) — on envoie seulement
        // les infos publiques. Les viewers récupèrent leur propre token via GET.
        broadcastLiveStarted(publicDtoOf(dto));
        return dto;
    }

    public List<LiveRoomDto> listActiveRooms() {
        // Liste publique : on masque les tokens streamer.
        List<LiveRoomDto> result = new ArrayList<>();
        for (LiveRoomDto dto : activeRooms.values()) {
            result.add(publicDtoOf(dto));
        }
        return result;
    }

    /**
     * Récupère une room et génère un token VIEWER frais à chaque appel
     * (canPublish=false, canSubscribe=true). Le token est valable tokenTtlSeconds.
     */
    public LiveRoomDto getRoomWithViewerToken(String roomName, String userId, String userName) {
        LiveRoomDto room = activeRooms.get(roomName);
        if (room == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "Live introuvable");
        }
        validateConfig();

        String identity = (userId != null && !userId.isBlank())
            ? "viewer-" + userId + "-" + UUID.randomUUID().toString().substring(0, 4)
            : "viewer-" + UUID.randomUUID().toString().substring(0, 8);

        String viewerToken = generateToken(roomName, identity, userName, false);

        LiveRoomDto copy = publicDtoOf(room);
        copy.setToken(viewerToken);
        return copy;
    }

    public void deleteRoom(String roomName) {
        LiveRoomDto removed = activeRooms.remove(roomName);
        if (removed != null) {
            log.info("[Live] Room terminée : {}", roomName);
            broadcastLiveEnded(removed);
        }
        // Pas besoin d'appeler l'API LiveKit : la room se ferme automatiquement
        // quand tous les participants se déconnectent. Pour forcer la fermeture
        // immédiate, on pourrait appeler POST /twirp/livekit.RoomService/DeleteRoom
        // mais ce n'est pas nécessaire pour notre cas d'usage.
    }

    // ───────── Génération JWT LiveKit ─────────

    private String generateToken(String roomName, String identity, String name, boolean canPublish) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(tokenTtlSeconds);

        Map<String, Object> videoGrant = new HashMap<>();
        videoGrant.put("room", roomName);
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", canPublish);
        videoGrant.put("canSubscribe", true);
        videoGrant.put("canPublishData", canPublish);

        SecretKey key = Keys.hmacShaKeyFor(livekitApiSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
            .setIssuer(livekitApiKey)
            .setSubject(identity)
            .claim("name", name)
            .claim("video", videoGrant)
            .setNotBefore(Date.from(now))
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(exp))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }

    private void validateConfig() {
        if (livekitApiKey == null || livekitApiKey.isBlank()
            || livekitApiSecret == null || livekitApiSecret.isBlank()
            || livekitWsUrl == null || livekitWsUrl.isBlank()) {
            throw new IllegalStateException(
                "Configuration LiveKit manquante : renseigne livekit.api.key, "
                + "livekit.api.secret et livekit.ws.url dans application.properties"
            );
        }
        if (livekitApiSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "livekit.api.secret doit faire au moins 32 octets pour HS256"
            );
        }
    }

    /** Retourne une copie du DTO sans le token (pour les flux publics). */
    private LiveRoomDto publicDtoOf(LiveRoomDto src) {
        LiveRoomDto copy = new LiveRoomDto();
        copy.setRoomName(src.getRoomName());
        copy.setRoomUrl(src.getRoomUrl());
        copy.setWsUrl(src.getWsUrl());
        copy.setStreamerUsername(src.getStreamerUsername());
        copy.setStreamerUserId(src.getStreamerUserId());
        copy.setTitle(src.getTitle());
        copy.setStartedAt(src.getStartedAt());
        copy.setViewerCount(src.getViewerCount());
        // token volontairement omis
        return copy;
    }

    // ───────── WebSocket helpers ─────────

    private void broadcastLiveStarted(LiveRoomDto dto) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "LIVE_STARTED");
        payload.put("room", dto);
        messagingTemplate.convertAndSend("/topic/live.events", payload);
    }

    private void broadcastLiveEnded(LiveRoomDto dto) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "LIVE_ENDED");
        payload.put("roomName", dto.getRoomName());
        messagingTemplate.convertAndSend("/topic/live.events", payload);
    }
}
