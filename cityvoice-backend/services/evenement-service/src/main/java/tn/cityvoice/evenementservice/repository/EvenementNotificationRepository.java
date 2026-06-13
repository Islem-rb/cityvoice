package tn.cityvoice.evenementservice.repository;

import tn.cityvoice.evenementservice.entity.EvenementNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface EvenementNotificationRepository
        extends JpaRepository<EvenementNotification, Long> {

    // Toutes les notifs d'un destinataire, plus récentes en premier
    List<EvenementNotification> findByDestinataireIdOrderByDateCreationDesc(
            String destinataireId);

    // Notifs non lues d'un destinataire
    List<EvenementNotification> findByDestinataireIdAndLuFalse(
            String destinataireId);

    // Compter les non lues
    long countByDestinataireIdAndLuFalse(String destinataireId);

    // Marquer toutes comme lues
    @Modifying
    @Transactional
    @Query("UPDATE EvenementNotification n SET n.lu = true WHERE n.destinataireId = :destinataireId")
    void marquerToutesLues(String destinataireId);
}