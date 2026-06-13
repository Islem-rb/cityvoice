package tn.cityvoice.actualiteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.actualiteservice.entity.Friendship;
import tn.cityvoice.actualiteservice.entity.Friendship.FriendshipStatus;
import tn.cityvoice.actualiteservice.repository.FriendshipRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Contrôleur « Amis ».
 *
 * L'entité Friendship appartient désormais au actualite-service : c'est ici qu'on
 * gère les relations (demandes, acceptation, blocage, etc.). Pour récupérer les
 * informations publiques d'un utilisateur (nom, photo, ville, gouvernorat), on
 * appelle le user-service via son endpoint public :
 *   GET http://localhost:8081/api/users/{id}/public
 */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class FriendController {

    private final FriendshipRepository friendshipRepo;

    /** Base URL du user-service — simple et explicite (même pattern que le reste du service). */
    private static final String USER_SERVICE_BASE = "http://localhost:8081/api/users";

    private final RestTemplate restTemplate = new RestTemplate();

    // ===== ENVOYER UNE DEMANDE D'AMI =====
    @PostMapping("/request/{requesterId}/{addresseeId}")
    public ResponseEntity<?> sendRequest(
            @PathVariable("requesterId") String requesterId,
            @PathVariable("addresseeId") String addresseeId) {

        if (requesterId.equals(addresseeId))
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de s'ajouter soi-même"));

        Optional<Friendship> existing = friendshipRepo.findBetween(requesterId, addresseeId);
        if (existing.isPresent())
            return ResponseEntity.badRequest().body(Map.of("error", "Relation déjà existante"));

        Friendship f = new Friendship();
        f.setRequesterId(requesterId);
        f.setAddresseeId(addresseeId);
        f.setStatus(FriendshipStatus.PENDING);
        friendshipRepo.save(f);
        return ResponseEntity.ok(Map.of("message", "Demande envoyée"));
    }

    // ===== ACCEPTER UNE DEMANDE =====
    @Transactional
    @PutMapping("/accept/{requesterId}/{addresseeId}")
    public ResponseEntity<?> acceptRequest(
            @PathVariable("requesterId") String requesterId,
            @PathVariable("addresseeId") String addresseeId) {

        Optional<Friendship> f = friendshipRepo.findBetween(requesterId, addresseeId);
        if (f.isEmpty()) return ResponseEntity.notFound().build();
        f.get().setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepo.save(f.get());
        return ResponseEntity.ok(Map.of("message", "Demande acceptée"));
    }

    // ===== REFUSER UNE DEMANDE =====
    @Transactional
    @PutMapping("/reject/{requesterId}/{addresseeId}")
    public ResponseEntity<?> rejectRequest(
            @PathVariable("requesterId") String requesterId,
            @PathVariable("addresseeId") String addresseeId) {

        Optional<Friendship> f = friendshipRepo.findBetween(requesterId, addresseeId);
        if (f.isEmpty()) return ResponseEntity.notFound().build();
        f.get().setStatus(FriendshipStatus.REJECTED);
        friendshipRepo.save(f.get());
        return ResponseEntity.ok(Map.of("message", "Demande refusée"));
    }

    // ===== SUPPRIMER UN AMI =====
    @Transactional
    @DeleteMapping("/remove/{userId}/{friendId}")
    public ResponseEntity<?> removeFriend(
            @PathVariable("userId") String userId,
            @PathVariable("friendId") String friendId) {

        Optional<Friendship> f = friendshipRepo.findBetween(userId, friendId);
        f.ifPresent(friendshipRepo::delete);
        return ResponseEntity.ok(Map.of("message", "Ami supprimé"));
    }

    // ===== BLOQUER UN AMI =====
    @Transactional
    @PutMapping("/block/{userId}/{friendId}")
    public ResponseEntity<?> blockFriend(
            @PathVariable("userId") String userId,
            @PathVariable("friendId") String friendId) {

        Optional<Friendship> f = friendshipRepo.findBetween(userId, friendId);
        if (f.isPresent()) {
            // Met à jour la relation existante
            f.get().setStatus(FriendshipStatus.BLOCKED);
            friendshipRepo.save(f.get());
        } else {
            // Crée une nouvelle relation BLOCKED
            Friendship blocked = new Friendship();
            blocked.setRequesterId(userId);
            blocked.setAddresseeId(friendId);
            blocked.setStatus(FriendshipStatus.BLOCKED);
            friendshipRepo.save(blocked);
        }
        return ResponseEntity.ok(Map.of("message", "Utilisateur bloqué"));
    }

    // ===== DÉBLOQUER =====
    @Transactional
    @PutMapping("/unblock/{userId}/{friendId}")
    public ResponseEntity<?> unblockFriend(
            @PathVariable("userId") String userId,
            @PathVariable("friendId") String friendId) {

        Optional<Friendship> f = friendshipRepo.findBetween(userId, friendId);
        f.ifPresent(friendshipRepo::delete);  // supprime la relation de blocage
        return ResponseEntity.ok(Map.of("message", "Utilisateur débloqué"));
    }

    // ===== LISTE DES BLOQUÉS =====
    @GetMapping("/blocked/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getBlockedUsers(
            @PathVariable("userId") String userId) {

        List<Friendship> blocked = friendshipRepo.findBlockedBy(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friendship f : blocked) {
            String blockedId = f.getAddresseeId(); // le bloqueur est toujours requesterId
            Map<String, Object> userInfo = fetchPublicUser(blockedId);
            if (userInfo != null) {
                result.add(buildUserMap(userInfo, "BLOCKED"));
            }
        }
        return ResponseEntity.ok(result);
    }

    // ===== LISTE DES AMIS =====
    @GetMapping("/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getFriends(
            @PathVariable("userId") String userId) {

        List<Friendship> friendships = friendshipRepo.findFriends(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friendship f : friendships) {
            String friendId = f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId();
            Map<String, Object> userInfo = fetchPublicUser(friendId);
            if (userInfo != null) {
                result.add(buildUserMap(userInfo, "FRIEND"));
            }
        }
        return ResponseEntity.ok(result);
    }

    // ===== DEMANDES REÇUES =====
    @GetMapping("/requests/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getPendingRequests(
            @PathVariable("userId") String userId) {

        List<Friendship> pending = friendshipRepo.findPendingReceived(userId);
        List<Map<String, Object>> result = pending.stream()
            .map(f -> {
                Map<String, Object> userInfo = fetchPublicUser(f.getRequesterId());
                if (userInfo == null) return Collections.<String, Object>emptyMap();
                Map<String, Object> map = new LinkedHashMap<>(buildUserMap(userInfo, "PENDING_RECEIVED"));
                map.put("requestedAt", f.getCreatedAt().toString());
                return map;
            })
            .filter(m -> !m.isEmpty())
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ===== STATUT ENTRE DEUX USERS =====
    @GetMapping("/status/{u1}/{u2}")
    public ResponseEntity<Map<String, String>> getStatus(
            @PathVariable("u1") String u1,
            @PathVariable("u2") String u2) {

        Optional<Friendship> f = friendshipRepo.findBetween(u1, u2);
        if (f.isEmpty()) return ResponseEntity.ok(Map.of("status", "NONE"));

        String status = f.get().getStatus().name();
        if (status.equals("PENDING")) {
            status = f.get().getRequesterId().equals(u1) ? "PENDING_SENT" : "PENDING_RECEIVED";
        }
        return ResponseEntity.ok(Map.of("status", status));
    }

    // ===== NOMBRE DE DEMANDES EN ATTENTE =====
    @GetMapping("/requests/count/{userId}")
    public ResponseEntity<Map<String, Long>> getPendingCount(
            @PathVariable("userId") String userId) {

        long count = friendshipRepo.countPendingReceived(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ===== HELPERS =====

    /**
     * Récupère les infos publiques d'un user via le user-service.
     * Retourne null en cas d'erreur (user inexistant, service down, etc.)
     * afin que la liste d'amis reste affichable même si un user a été supprimé.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchPublicUser(String userId) {
        try {
            return restTemplate.getForObject(
                USER_SERVICE_BASE + "/" + userId + "/public",
                Map.class
            );
        } catch (Exception e) {
            return null;
        }
    }

    /** Construit la map retournée au frontend (même format qu'avant le déplacement). */
    private Map<String, Object> buildUserMap(Map<String, Object> userInfo, String status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",           userInfo.getOrDefault("id", ""));
        map.put("nom",          userInfo.getOrDefault("nom", ""));
        map.put("photo",        userInfo.getOrDefault("photo", ""));
        map.put("ville",        userInfo.getOrDefault("ville", ""));
        map.put("gouvernorat",  userInfo.getOrDefault("gouvernorat", ""));
        map.put("status",       status);
        return map;
    }
}
