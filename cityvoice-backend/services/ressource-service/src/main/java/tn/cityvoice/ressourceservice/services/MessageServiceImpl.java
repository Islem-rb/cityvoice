package tn.cityvoice.ressourceservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.ressourceservice.entity.Message;
import tn.cityvoice.ressourceservice.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    @Override
    public Message envoyerMessage(Message message) {
        System.out.println("📨 Envoi de message de: " + message.getExpediteurNom());
        message.setDateEnvoi(LocalDateTime.now());
        message.setLu(false);
        return messageRepository.save(message);
    }

    @Override
    public List<Message> getConversation(String userId1, String userId2) {
        System.out.println("💬 Conversation entre: " + userId1 + " et " + userId2);
        return messageRepository.getConversation(userId1, userId2);
    }

    @Override
    public List<Message> getMessagesByDemande(Long demandeId) {
        System.out.println("💬 Messages pour demande: " + demandeId);
        return messageRepository.findByDemandeIdOrderByDateEnvoiAsc(demandeId);
    }

    @Override
    public List<Message> getMessagesNonLus(String userId) {
        System.out.println("🔔 Messages non lus pour: " + userId);
        return messageRepository.findByDestinataireIdAndLuFalseOrderByDateEnvoiDesc(userId);
    }

    @Override
    public void marquerCommeLu(Long messageId) {
        System.out.println("✅ Marquage message comme lu: " + messageId);
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message non trouvé avec id: " + messageId));
        message.setLu(true);
        message.setDateLecture(LocalDateTime.now());
        messageRepository.save(message);
    }
}