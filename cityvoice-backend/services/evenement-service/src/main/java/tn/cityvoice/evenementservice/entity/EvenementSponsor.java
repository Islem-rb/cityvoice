package tn.cityvoice.evenementservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "evenement_sponsors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvenementSponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evenement_id", nullable = false)
    @ToString.Exclude
    private Evenement evenement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sponsor_id", nullable = false)
    @ToString.Exclude
    private Sponsor sponsor;

    private String niveauSponsorat; // BRONZE, ARGENT, OR, PLATINE

    @Column(precision = 10, scale = 3)
    private BigDecimal montantSponsorat;
    // Feedback post-événement
    private Boolean satisfait;
    private String retourInvestissement; // FAIBLE, MOYEN, ELEVE
    @Builder.Default
    private Boolean renouvele = false;
}