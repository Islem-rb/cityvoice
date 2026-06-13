package tn.cityvoice.actualiteservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_messages")
@Data
@NoArgsConstructor
public class GroupMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID du groupe auquel appartient le message */
    @Column(nullable = false)
    private Long groupId;

    /** Expéditeur */
    @Column(nullable = false)
    private String senderId;

    /** Nom et photo (dénormalisés pour éviter inter-service) */
    private String senderName;
    private String senderPhoto;

    /** Contenu du message */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        if (sentAt == null) sentAt = LocalDateTime.now();
    }
}
