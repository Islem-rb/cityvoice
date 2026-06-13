package tn.cityvoice.actualiteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.actualiteservice.dto.ChatGroupDTO;
import tn.cityvoice.actualiteservice.dto.GroupMessageDTO;
import tn.cityvoice.actualiteservice.entity.ChatGroup;
import tn.cityvoice.actualiteservice.entity.GroupMessage;
import tn.cityvoice.actualiteservice.repository.ChatGroupRepository;
import tn.cityvoice.actualiteservice.repository.GroupMessageRepository;
import tn.cityvoice.actualiteservice.service.BadWordsService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class GroupChatController {

    private final ChatGroupRepository groupRepo;
    private final GroupMessageRepository msgRepo;
    private final BadWordsService badWordsService;

    // ── Créer un groupe ──────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ChatGroupDTO> createGroup(@RequestBody Map<String, Object> body) {
        String name      = (String) body.get("name");
        String creatorId = (String) body.get("creatorId");
        @SuppressWarnings("unchecked")
        List<String> memberIds = (List<String>) body.getOrDefault("memberIds", new ArrayList<>());

        ChatGroup group = new ChatGroup();
        group.setName(name);
        group.setCreatorId(creatorId);

        // Toujours inclure le créateur dans les membres
        Set<String> members = new LinkedHashSet<>();
        members.add(creatorId);
        members.addAll(memberIds);
        group.setMemberIds(String.join(",", members));

        return ResponseEntity.ok(toDTO(groupRepo.save(group)));
    }

    // ── Groupes d'un utilisateur ─────────────────────────────────
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ChatGroupDTO>> getGroupsForUser(@PathVariable String userId) {
        List<ChatGroupDTO> groups = groupRepo.findGroupsForUser(userId)
                .stream()
                .filter(g -> g.hasMember(userId))   // double-vérification
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(groups);
    }

    // ── Détail d'un groupe ───────────────────────────────────────
    @GetMapping("/{groupId}")
    public ResponseEntity<ChatGroupDTO> getGroup(@PathVariable Long groupId) {
        return groupRepo.findById(groupId)
                .map(g -> ResponseEntity.ok(toDTO(g)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Ajouter des membres ──────────────────────────────────────
    @PutMapping("/{groupId}/members")
    public ResponseEntity<ChatGroupDTO> addMembers(@PathVariable Long groupId,
                                                    @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> newMembers = (List<String>) body.get("memberIds");
        return groupRepo.findById(groupId).map(group -> {
            if (newMembers != null) newMembers.forEach(group::addMember);
            return ResponseEntity.ok(toDTO(groupRepo.save(group)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Quitter / exclure un membre ──────────────────────────────
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<ChatGroupDTO> removeMember(@PathVariable Long groupId,
                                                      @PathVariable String userId) {
        return groupRepo.findById(groupId).map(group -> {
            group.removeMember(userId);
            return ResponseEntity.ok(toDTO(groupRepo.save(group)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Kick (exclure par l'admin) ───────────────────────────────
    @DeleteMapping("/{groupId}/kick/{userId}")
    public ResponseEntity<?> kickMember(@PathVariable Long groupId,
                                         @PathVariable String userId,
                                         @RequestParam String requesterId) {
        return groupRepo.findById(groupId).map(group -> {
            if (!group.getCreatorId().equals(requesterId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Seul le créateur peut exclure des membres"));
            }
            if (userId.equals(requesterId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vous ne pouvez pas vous exclure vous-même"));
            }
            group.removeMember(userId);
            return ResponseEntity.ok((Object) toDTO(groupRepo.save(group)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Bloquer un membre (exclure + interdire de rejoindre) ─────
    @PostMapping("/{groupId}/block/{userId}")
    public ResponseEntity<?> blockMember(@PathVariable Long groupId,
                                          @PathVariable String userId,
                                          @RequestBody Map<String, String> body) {
        String requesterId = body.get("requesterId");
        return groupRepo.findById(groupId).map(group -> {
            if (!group.getCreatorId().equals(requesterId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Seul le créateur peut bloquer des membres"));
            }
            if (userId.equals(requesterId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vous ne pouvez pas vous bloquer vous-même"));
            }
            group.blockMember(userId);
            return ResponseEntity.ok((Object) toDTO(groupRepo.save(group)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Débloquer un membre ──────────────────────────────────────
    @PostMapping("/{groupId}/unblock/{userId}")
    public ResponseEntity<?> unblockMember(@PathVariable Long groupId,
                                            @PathVariable String userId,
                                            @RequestBody Map<String, String> body) {
        String requesterId = body.get("requesterId");
        return groupRepo.findById(groupId).map(group -> {
            if (!group.getCreatorId().equals(requesterId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Seul le créateur peut débloquer des membres"));
            }
            group.unblockMember(userId);
            return ResponseEntity.ok((Object) toDTO(groupRepo.save(group)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Envoyer un message dans le groupe ────────────────────────
    @PostMapping("/{groupId}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable Long groupId,
                                          @RequestBody Map<String, String> body) {
        // Vérifier que le groupe existe
        Optional<ChatGroup> groupOpt = groupRepo.findById(groupId);
        if (groupOpt.isEmpty()) return ResponseEntity.notFound().build();
        ChatGroup grp = groupOpt.get();

        // Vérifier que le sender n'est pas bloqué
        String senderId = body.get("senderId");
        if (grp.isBlocked(senderId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Vous avez été bloqué dans ce groupe"));
        }

        GroupMessage msg = new GroupMessage();
        msg.setGroupId(groupId);
        msg.setSenderId(body.get("senderId"));
        msg.setSenderName(body.getOrDefault("senderName", ""));
        msg.setSenderPhoto(body.getOrDefault("senderPhoto", ""));
        // Filtrer les mots interdits avant sauvegarde
        msg.setContent(badWordsService.filter(body.get("content")));

        return ResponseEntity.ok(toMsgDTO(msgRepo.save(msg)));
    }

    // ── Historique complet ───────────────────────────────────────
    @GetMapping("/{groupId}/messages")
    public ResponseEntity<List<GroupMessageDTO>> getHistory(@PathVariable Long groupId) {
        List<GroupMessageDTO> msgs = msgRepo
                .findByGroupIdOrderBySentAtAsc(groupId)
                .stream().map(this::toMsgDTO).collect(Collectors.toList());
        return ResponseEntity.ok(msgs);
    }

    // ── Nouveaux messages depuis lastId (polling) ────────────────
    @GetMapping("/{groupId}/messages/new/{lastId}")
    public ResponseEntity<List<GroupMessageDTO>> getNewMessages(@PathVariable Long groupId,
                                                                 @PathVariable Long lastId) {
        List<GroupMessageDTO> msgs = msgRepo
                .findByGroupIdAndIdGreaterThanOrderBySentAtAsc(groupId, lastId)
                .stream().map(this::toMsgDTO).collect(Collectors.toList());
        return ResponseEntity.ok(msgs);
    }

    // ── Mappers ──────────────────────────────────────────────────
    private ChatGroupDTO toDTO(ChatGroup g) {
        ChatGroupDTO dto = new ChatGroupDTO();
        dto.setId(g.getId());
        dto.setName(g.getName());
        dto.setCreatorId(g.getCreatorId());
        dto.setPhotoUrl(g.getPhotoUrl());
        dto.setCreatedAt(g.getCreatedAt());

        List<String> members = new ArrayList<>();
        if (g.getMemberIds() != null && !g.getMemberIds().isBlank()) {
            members = Arrays.asList(g.getMemberIds().split(","));
        }
        dto.setMemberIds(members);

        List<String> blocked = new ArrayList<>();
        if (g.getBlockedMemberIds() != null && !g.getBlockedMemberIds().isBlank()) {
            blocked = Arrays.asList(g.getBlockedMemberIds().split(","));
        }
        dto.setBlockedMemberIds(blocked);

        return dto;
    }

    private GroupMessageDTO toMsgDTO(GroupMessage m) {
        GroupMessageDTO dto = new GroupMessageDTO();
        dto.setId(m.getId());
        dto.setGroupId(m.getGroupId());
        dto.setSenderId(m.getSenderId());
        dto.setSenderName(m.getSenderName());
        dto.setSenderPhoto(m.getSenderPhoto());
        dto.setContent(m.getContent());
        dto.setSentAt(m.getSentAt());
        return dto;
    }
}
