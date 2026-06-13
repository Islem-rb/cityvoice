package tn.cityvoice.projetservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import tn.cityvoice.projetservice.entity.enums.StatutCollecte;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "collecte_financement")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CollecteFinancement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projet_id", nullable = false, unique = true)
    private Projet projet;

    @Column(nullable = false)
    private Float montantCible;

    @Builder.Default
    private Float montantCollecte = 0f;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutCollecte statut = StatutCollecte.ACTIVE;

    @JsonIgnore
    @OneToMany(mappedBy = "collecte",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    private List<Paiement> paiements;

    @PrePersist
    public void prePersist() {
        if (this.dateDebut       == null) this.dateDebut       = LocalDateTime.now();
        if (this.statut          == null) this.statut          = StatutCollecte.ACTIVE;
        if (this.montantCollecte == null) this.montantCollecte = 0f;
    }
}