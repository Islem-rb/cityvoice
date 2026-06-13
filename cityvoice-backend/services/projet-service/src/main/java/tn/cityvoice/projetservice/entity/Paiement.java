package tn.cityvoice.projetservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import tn.cityvoice.projetservice.entity.enums.MethodePaiement;
import tn.cityvoice.projetservice.entity.enums.StatutPaiement;
import java.time.LocalDateTime;

@Entity
@Table(name = "paiement")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collecte_id", nullable = false)
    private CollecteFinancement collecte;

    private String userId;

    @Column(nullable = false)
    private Float montant;

    @Builder.Default
    private Boolean anonymous = false;

    private String email;
    private String phone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MethodePaiement methode;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutPaiement statut = StatutPaiement.EN_ATTENTE;

    private String reference;
    private LocalDateTime date;

    @PrePersist
    public void prePersist() {
        this.date      = LocalDateTime.now();
        this.reference = "PAY-" + System.currentTimeMillis();
        if (this.statut    == null) this.statut    = StatutPaiement.EN_ATTENTE;
        if (this.anonymous == null) this.anonymous = false;
    }
}