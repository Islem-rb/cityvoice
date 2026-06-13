package tn.cityvoice.ressourceservice.repository;

import tn.cityvoice.ressourceservice.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE (m.expediteurId = ?1 AND m.destinataireId = ?2) OR (m.expediteurId = ?2 AND m.destinataireId = ?1) ORDER BY m.dateEnvoi ASC")
    List<Message> getConversation(String userId1, String userId2);

    List<Message> findByDemandeIdOrderByDateEnvoiAsc(Long demandeId);

    List<Message> findByDestinataireIdAndLuFalseOrderByDateEnvoiDesc(String destinataireId);
}