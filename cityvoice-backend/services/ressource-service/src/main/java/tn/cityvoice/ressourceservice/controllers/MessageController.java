package tn.cityvoice.ressourceservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.ressourceservice.entity.Message;
import tn.cityvoice.ressourceservice.services.MessageService;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<Message> envoyerMessage(@RequestBody Message message) {
        return ResponseEntity.ok(messageService.envoyerMessage(message));
    }

    @GetMapping("/conversation/{userId1}/{userId2}")
    public ResponseEntity<List<Message>> getConversation(
            @PathVariable("userId1") String userId1,
            @PathVariable("userId2") String userId2) {
        return ResponseEntity.ok(messageService.getConversation(userId1, userId2));
    }

    @GetMapping("/demande/{demandeId}")
    public ResponseEntity<List<Message>> getMessagesByDemande(
            @PathVariable("demandeId") Long demandeId) {  // ← Ajoutez l'annotation avec le nom
        return ResponseEntity.ok(messageService.getMessagesByDemande(demandeId));
    }

    @GetMapping("/non-lus/{userId}")
    public ResponseEntity<List<Message>> getMessagesNonLus(
            @PathVariable("userId") String userId) {
        return ResponseEntity.ok(messageService.getMessagesNonLus(userId));
    }

    @PutMapping("/{id}/lu")
    public ResponseEntity<Void> marquerCommeLu(
            @PathVariable("id") Long id) {
        messageService.marquerCommeLu(id);
        return ResponseEntity.ok().build();
    }
}