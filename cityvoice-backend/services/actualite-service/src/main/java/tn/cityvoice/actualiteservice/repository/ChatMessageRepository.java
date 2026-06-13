package tn.cityvoice.actualiteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.cityvoice.actualiteservice.entity.ChatMessage;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Historique de conversation entre 2 users, trié par date
    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.senderId = :user1 AND m.receiverId = :user2) OR " +
           "(m.senderId = :user2 AND m.receiverId = :user1) " +
           "ORDER BY m.sentAt ASC")
    List<ChatMessage> findConversation(@Param("user1") String user1, @Param("user2") String user2);

    // Nouveaux messages depuis un ID (pour polling)
    @Query("SELECT m FROM ChatMessage m WHERE " +
           "((m.senderId = :user1 AND m.receiverId = :user2) OR " +
           "(m.senderId = :user2 AND m.receiverId = :user1)) AND " +
           "m.id > :lastId " +
           "ORDER BY m.sentAt ASC")
    List<ChatMessage> findNewMessages(@Param("user1") String user1,
                                      @Param("user2") String user2,
                                      @Param("lastId") Long lastId);

    // Nombre de messages non lus pour un receiver (champ 'read', pas 'isRead')
    @Query("SELECT COUNT(m) FROM ChatMessage m " +
           "WHERE m.receiverId = :receiverId AND m.senderId = :senderId AND m.read = false")
    long countUnread(@Param("receiverId") String receiverId,
                     @Param("senderId") String senderId);

    // Marquer comme lus
    @Modifying
    @Query("UPDATE ChatMessage m SET m.read = true " +
           "WHERE m.receiverId = :receiverId AND m.senderId = :senderId AND m.read = false")
    void markAsRead(@Param("receiverId") String receiverId,
                    @Param("senderId") String senderId);

    // Derniers contacts (users avec qui on a échangé)
    @Query("SELECT DISTINCT CASE WHEN m.senderId = :userId THEN m.receiverId ELSE m.senderId END " +
           "FROM ChatMessage m WHERE m.senderId = :userId OR m.receiverId = :userId")
    List<String> findContactIds(@Param("userId") String userId);

    // ID du dernier message ENVOYÉ par senderId à receiverId qui a été lu (indicateur "Vu")
    @Query("SELECT COALESCE(MAX(m.id), -1) FROM ChatMessage m " +
           "WHERE m.senderId = :senderId AND m.receiverId = :receiverId AND m.read = true")
    long findLastSeenId(@Param("senderId") String senderId,
                        @Param("receiverId") String receiverId);
}
