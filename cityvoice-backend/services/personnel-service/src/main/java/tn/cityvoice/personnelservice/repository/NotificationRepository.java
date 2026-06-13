package tn.cityvoice.personnelservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.personnelservice.entity.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // ✅ CORRECT (receiverId)
    List<Notification> findByReceiverIdOrderByCreatedAtDesc(UUID receiverId);

    List<Notification> findByReceiverIdAndReadFalseOrderByCreatedAtDesc(UUID receiverId);

    long countByReceiverIdAndReadFalse(UUID receiverId);

    boolean existsByReceiverIdAndCvIdAndType(UUID receiverId, UUID cvId, String type);
}
