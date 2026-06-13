package tn.cityvoice.evenementservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import tn.cityvoice.evenementservice.enums.TypeEvenement;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "suggestions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private TypeEvenement typeSouhaite;

    private String lieuSouhaite;
    private LocalDate dateSouhaitee;

    private String citoyenId;
    private String emailCitoyen;

    @Builder.Default
    private String statut = "SOUMISE";

    @Column(name = "commentaire_admin", columnDefinition = "TEXT")
    private String commentaireAdmin;

    @CreationTimestamp
    private LocalDateTime soumisLe;
}