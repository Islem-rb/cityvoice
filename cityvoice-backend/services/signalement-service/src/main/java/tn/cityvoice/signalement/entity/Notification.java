package tn.cityvoice.signalement.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import tn.cityvoice.signalement.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Notification persistée en base.
 * Chaque notification cible un utilisateur (destinataireId).
 * Elle est envoyée en temps réel via WebSocket ET stockée pour le polling.
 */
@Entity
@Table(name = "notifications",
       indexes = {
           @Index(name = "idx_notif_destinataire", columnList = "destinataire_id"),
           @Index(name = "idx_notif_lu",           columnList = "lu")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID de l'utilisateur destinataire */
    @Column(name = "destinataire_id", nullable = false, length = 60)
    private String destinataireId;

    /** Type de la notification (détermine l'icône côté Angular) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /** Message affiché dans la cloche */
    @Column(nullable = false, length = 500)
    private String message;

    /** Lien de navigation optionnel (ex: /signaler/mes-signalements/63) */
    @Column(length = 255)
    private String lien;

    /** ID de l'entité source (signalement, contrat, article…) */
    private Long entiteId;

    /** Lu ou non */
    @Builder.Default
    @Column(nullable = false)
    private Boolean lu = false;

    @CreationTimestamp
    @Column(updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateCreation;
}
