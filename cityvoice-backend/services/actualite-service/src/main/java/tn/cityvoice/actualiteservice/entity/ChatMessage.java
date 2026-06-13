package tn.cityvoice.actualiteservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String senderId;

    @Column(nullable = false)
    private String receiverId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    /**
     * Renommé de 'isRead' à 'read' pour éviter l'ambiguïté Hibernate/JPQL.
     * Lombok génère isRead() getter et setRead() setter pour 'boolean read'.
     * @Column(name = "is_read") conserve le nom de colonne existant en DB.
     */
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @PrePersist
    public void prePersist() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}
