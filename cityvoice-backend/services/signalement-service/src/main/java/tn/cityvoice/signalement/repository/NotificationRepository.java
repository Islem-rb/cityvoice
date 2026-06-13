package tn.cityvoice.signalement.repository;

import tn.cityvoice.signalement.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Toutes les notifs d'un utilisateur, les plus récentes en premier */
    List<Notification> findByDestinataireIdOrderByDateCreationDesc(String destinataireId);

    /** Notifs non lues d'un utilisateur */
    List<Notification> findByDestinataireIdAndLuFalseOrderByDateCreationDesc(String destinataireId);

    /** Nombre de notifs non lues */
    long countByDestinataireIdAndLuFalse(String destinataireId);

    /** Marquer toutes les notifs d'un utilisateur comme lues */
    @Modifying
    @Query("UPDATE Notification n SET n.lu = true WHERE n.destinataireId = :uid")
    void markAllReadByUser(@Param("uid") String uid);
}
