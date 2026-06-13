package tn.cityvoice.personnelservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    UUID receiverId;

    @Column(name = "sender_user_id", nullable = false)
    UUID senderId;

    @Column(nullable = false)
    String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    String message;

    @Column(nullable = false)
    String type;

    // CORRECTION : forcer le nom JSON à "read" pour éviter le conflit isRead/read de Lombok+Jackson
    @JsonProperty("read")
    @Column(name = "is_read", nullable = false)
    boolean read = false;

    @Column(nullable = false)
    LocalDateTime createdAt;

    @Column
    UUID cvId;

    @Column
    String fonction;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}