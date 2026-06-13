package tn.cityvoice.ressourceservice.services;

import tn.cityvoice.ressourceservice.entity.Message;
import java.util.List;

public interface MessageService {

    Message envoyerMessage(Message message);

    List<Message> getConversation(String userId1, String userId2);

    List<Message> getMessagesByDemande(Long demandeId);

    List<Message> getMessagesNonLus(String userId);

    void marquerCommeLu(Long messageId);
}