package tn.cityvoice.evenementservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import tn.cityvoice.evenementservice.enums.StatutPaiement;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"evenement_id", "citoyen_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne
    @JoinColumn(name = "evenement_id")
    private Evenement evenement;

    @Column(name = "citoyen_id", nullable = false)
    private String  citoyenId;

    private String emailCitoyen;
    private String nomCitoyen;
    private String telCitoyen;
    @Builder.Default
    private Boolean confirme = false;

    @Builder.Default
    private Boolean rappelEnvoye = false;

    @Enumerated(EnumType.STRING)
    private StatutPaiement statutPaiement;


    @CreationTimestamp
    private LocalDateTime inscritLe;
    @Column(unique = true, nullable = false, updatable = false)
    private String qrToken;                    // UUID unique généré automatiquement

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPresence statutPresence = StatutPresence.EN_ATTENTE;

    private LocalDateTime datePresenceConfirmee;


    public enum StatutPresence {
        EN_ATTENTE,   // inscrit, pas encore scanné
        CONFIRME,     // scanné
        ABSENT        // pas venu
    }

    @PrePersist
    public void prePersist() {
        if (this.qrToken == null) {
            this.qrToken = UUID.randomUUID().toString();
        }
        if (this.statutPresence == null) {
            this.statutPresence = StatutPresence.EN_ATTENTE;
        }
    }
}