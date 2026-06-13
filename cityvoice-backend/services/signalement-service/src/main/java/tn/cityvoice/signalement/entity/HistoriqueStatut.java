package tn.cityvoice.signalement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tn.cityvoice.signalement.enums.StatutSignalement;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "historique_statuts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoriqueStatut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signalement_id", nullable = false)
    @ToString.Exclude
    private Signalement signalement;

    @Enumerated(EnumType.STRING)
    private StatutSignalement ancienStatut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutSignalement nouveauStatut;

    private String commentaire;

    private String modifiePar;   // ID de l'utilisateur qui a changé le statut

    @CreationTimestamp
    private LocalDateTime dateChangement;
}
