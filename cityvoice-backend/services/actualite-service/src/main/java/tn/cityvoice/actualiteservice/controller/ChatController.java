package tn.cityvoice.actualiteservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.actualiteservice.dto.ChatMessageDTO;
import tn.cityvoice.actualiteservice.dto.ChatSendRequest;
import tn.cityvoice.actualiteservice.entity.ChatMessage;
import tn.cityvoice.actualiteservice.repository.ChatMessageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ChatController {

    private final ChatMessageRepository chatRepo;

    // ===== ENVOYER UN MESSAGE =====
    @PostMapping("/send")
    public ResponseEntity<ChatMessageDTO> sendMessage(@RequestBody ChatSendRequest req) {
        ChatMessage msg = new ChatMessage();
        msg.setSenderId(req.getSenderId());
        msg.setReceiverId(req.getReceiverId());
        msg.setContent(req.getContent());
        msg.setSentAt(LocalDateTime.now());
        msg.setRead(false);   // Lombok: setRead() pour 'boolean read'
        chatRepo.save(msg);
        return ResponseEntity.ok(toDTO(msg));
    }

    // ===== HISTORIQUE CONVERSATION =====
    @GetMapping("/history/{user1}/{user2}")
    public ResponseEntity<List<ChatMessageDTO>> getHistory(
            @PathVariable("user1") String user1,
            @PathVariable("user2") String user2) {
        List<ChatMessage> messages = chatRepo.findConversation(user1, user2);
        return ResponseEntity.ok(messages.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    // ===== NOUVEAUX MESSAGES (polling) =====
    @GetMapping("/new/{user1}/{user2}/{lastId}")
    public ResponseEntity<List<ChatMessageDTO>> getNewMessages(
            @PathVariable("user1") String user1,
            @PathVariable("user2") String user2,
            @PathVariable("lastId") Long lastId) {
        List<ChatMessage> messages = chatRepo.findNewMessages(user1, user2, lastId);
        return ResponseEntity.ok(messages.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    // ===== MARQUER COMME LUS =====
    @Transactional
    @PutMapping("/read/{receiverId}/{senderId}")
    public ResponseEntity<Void> markAsRead(
            @PathVariable("receiverId") String receiverId,
            @PathVariable("senderId") String senderId) {
        chatRepo.markAsRead(receiverId, senderId);
        return ResponseEntity.ok().build();
    }

    // ===== NOMBRE DE NON-LUS =====
    @GetMapping("/unread/{receiverId}/{senderId}")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @PathVariable("receiverId") String receiverId,
            @PathVariable("senderId") String senderId) {
        long count = chatRepo.countUnread(receiverId, senderId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ===== DERNIER MESSAGE "VU" (pour l'indicateur seen côté expéditeur) =====
    @GetMapping("/seen/{senderId}/{receiverId}")
    public ResponseEntity<Map<String, Long>> getLastSeen(
            @PathVariable("senderId") String senderId,
            @PathVariable("receiverId") String receiverId) {
        long lastSeenId = chatRepo.findLastSeenId(senderId, receiverId);
        return ResponseEntity.ok(Map.of("lastSeenId", lastSeenId));
    }

    // ===== CONVERTIR EN DTO =====
    private ChatMessageDTO toDTO(ChatMessage msg) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(msg.getId());
        dto.setSenderId(msg.getSenderId());
        dto.setReceiverId(msg.getReceiverId());
        dto.setContent(msg.getContent());
        dto.setSentAt(msg.getSentAt());
        dto.setRead(msg.isRead());   // Lombok: isRead() getter pour 'boolean read'
        return dto;
    }
}
