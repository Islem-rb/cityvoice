package tn.cityvoice.actualiteservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** L'utilisateur qui reçoit la notification */
    @Column(nullable = false)
    private String recipientId;

    /** L'utilisateur qui a déclenché l'action (réaction, commentaire, partage) */
    @Column(nullable = false)
    private String actorId;

    /** Nom affiché de l'acteur (dénormalisé pour éviter un appel inter-service) */
    private String actorName;

    /** Photo de l'acteur */
    private String actorPhoto;

    /**
     * Type de notification :
     * REACTION, COMMENT, SHARE, FRIEND_REQUEST, FRIEND_ACCEPTED
     */
    @Column(nullable = false)
    private String type;

    /** Message lisible par l'utilisateur */
    @Column(nullable = false)
    private String message;

    /** ID du post concerné (nullable si non applicable) */
    private Long postId;

    // "read" est un mot réservé MySQL → renommé is_read
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
