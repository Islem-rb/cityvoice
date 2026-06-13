package tn.cityvoice.actualiteservice.live;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commentaires en direct pour les lives (réaction texte façon "chat du stream").
 *
 * Deux canaux :
 *   1) REST  (POST/GET) pour poster + récupérer l'historique à l'arrivée.
 *   2) STOMP : diffusion en temps réel sur /topic/live.{roomName}.comments.
 *
 * Les spectateurs qui regardent le live :
 *   - s'abonnent à /topic/live.{roomName}.comments pour voir les nouveaux messages,
 *   - POSTent sur /api/live/{roomName}/comments pour poster (on persiste puis on
 *     broadcast dans la foulée — pas besoin d'un @MessageMapping côté client).
 *
 * On reste cohérent avec le WebSocketConfig existant :
 *   - prefix /app pour les @MessageMapping,
 *   - broker in-memory /topic.
 */
@RestController
@RequestMapping("/api/live")
@CrossOrigin(origins = "http://localhost:4200")
public class LiveCommentController {

    private static final int MAX_LEN = 500;
    private static final int HISTORY_LIMIT = 200;

    private final LiveCommentRepository repo;
    private final SimpMessagingTemplate messagingTemplate;

    public LiveCommentController(LiveCommentRepository repo,
                                 SimpMessagingTemplate messagingTemplate) {
        this.repo = repo;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Poste un commentaire dans le live. Persiste en DB et broadcast sur le topic
     * dédié à la room. L'UI du streamer ET des viewers reçoivent le message au
     * même instant.
     */
    @PostMapping("/{roomName}/comments")
    public ResponseEntity<LiveCommentDto> post(@PathVariable String roomName,
                                               @RequestBody LiveCommentDto body) {
        if (body == null || body.getContenu() == null || body.getContenu().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        LiveComment c = new LiveComment();
        c.setRoomName(roomName);
        c.setAuteurId(body.getAuteurId());
        c.setAuteurNom(safe(body.getAuteurNom(), "Spectateur"));
        c.setAuteurPhoto(body.getAuteurPhoto());
        c.setContenu(trimContent(body.getContenu()));
        c.setDate(LocalDateTime.now());

        LiveComment saved = repo.save(c);
        LiveCommentDto dto = LiveCommentDto.from(saved);

        // Diffusion temps réel — chaque live a son propre topic.
        messagingTemplate.convertAndSend("/topic/live." + roomName + ".comments", dto);

        return ResponseEntity.ok(dto);
    }

    /**
     * Historique des commentaires d'une room (utilisé quand un viewer arrive
     * après le début du live pour voir ce qui a déjà été dit).
     */
    @GetMapping("/{roomName}/comments")
    public ResponseEntity<List<LiveCommentDto>> history(@PathVariable String roomName) {
        List<LiveComment> all = repo.findByRoomNameOrderByDateAsc(roomName);
        // On limite à HISTORY_LIMIT derniers messages pour ne pas spammer l'UI
        List<LiveComment> trimmed = all.size() > HISTORY_LIMIT
                ? all.subList(all.size() - HISTORY_LIMIT, all.size())
                : all;
        List<LiveCommentDto> dtos = trimmed.stream()
                .map(LiveCommentDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Nettoyage : supprime tous les commentaires d'une room (ex: à la fin du
     * live). Appelé depuis le front ou depuis {@link LiveService#deleteRoom}
     * si on veut brancher une purge automatique.
     */
    @Transactional
    @DeleteMapping("/{roomName}/comments")
    public ResponseEntity<Void> clear(@PathVariable String roomName) {
        repo.deleteByRoomName(roomName);
        return ResponseEntity.noContent().build();
    }

    /**
     * Variante STOMP : un client peut envoyer à /app/live.{roomName}.comment
     * au lieu du POST REST. Utile pour les très hauts débits de messages,
     * mais pour notre cas le REST + broadcast suffit largement. On garde
     * quand même l'entrée STOMP pour flexibilité côté front.
     */
    @MessageMapping("/live.{roomName}.comment")
    public void receive(@DestinationVariable String roomName, LiveCommentDto body) {
        if (body == null || body.getContenu() == null || body.getContenu().isBlank()) return;

        LiveComment c = new LiveComment();
        c.setRoomName(roomName);
        c.setAuteurId(body.getAuteurId());
        c.setAuteurNom(safe(body.getAuteurNom(), "Spectateur"));
        c.setAuteurPhoto(body.getAuteurPhoto());
        c.setContenu(trimContent(body.getContenu()));
        c.setDate(LocalDateTime.now());

        LiveComment saved = repo.save(c);
        messagingTemplate.convertAndSend(
                "/topic/live." + roomName + ".comments",
                LiveCommentDto.from(saved)
        );
    }

    // ───────── helpers ─────────

    private static String trimContent(String raw) {
        String s = raw.trim();
        return s.length() > MAX_LEN ? s.substring(0, MAX_LEN) : s;
    }

    private static String safe(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
