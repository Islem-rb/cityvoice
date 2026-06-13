package tn.cityvoice.actualiteservice.live;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LiveController — endpoints REST pour les lives LiveKit.
 *
 * Base URL : http://localhost:8083/api/live
 *   POST   /api/live/create        → crée une room + renvoie un token streamer
 *   GET    /api/live/list          → liste les lives actifs (sans token)
 *   GET    /api/live/{roomName}    → détails + token VIEWER fraîchement généré
 *   DELETE /api/live/{roomName}    → termine le live
 */
@RestController
@RequestMapping("/api/live")
@CrossOrigin(origins = "http://localhost:4200")
public class LiveController {

    private final LiveService liveService;

    public LiveController(LiveService liveService) {
        this.liveService = liveService;
    }

    @PostMapping("/create")
    public ResponseEntity<LiveRoomDto> createLive(@RequestBody CreateLiveRequest request) {
        String username = request.getUsername() != null ? request.getUsername() : "Citoyen";
        LiveRoomDto room = liveService.createRoom(username, request.getTitle(), request.getUserId());
        return ResponseEntity.ok(room);
    }

    @GetMapping("/list")
    public ResponseEntity<List<LiveRoomDto>> listLives() {
        return ResponseEntity.ok(liveService.listActiveRooms());
    }

    @DeleteMapping("/{roomName}")
    public ResponseEntity<Void> endLive(@PathVariable String roomName) {
        liveService.deleteRoom(roomName);
        return ResponseEntity.noContent().build();
    }

    /**
     * Renvoie un token VIEWER frais à chaque appel. Le nom du viewer est pris
     * dans les query params ?userId=…&userName=… (facultatifs — fallback aléatoire).
     */
    @GetMapping("/{roomName}")
    public ResponseEntity<LiveRoomDto> getLive(
            @PathVariable String roomName,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userName) {
        LiveRoomDto room = liveService.getRoomWithViewerToken(roomName, userId, userName);
        return ResponseEntity.ok(room);
    }
}
