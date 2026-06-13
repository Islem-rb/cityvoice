package tn.cityvoice.signalement.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import tn.cityvoice.signalement.enums.StatutContrat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Contrat de travail généré automatiquement par l'IA après affectation d'équipe.
 * Cycle de vie :
 *   EN_ATTENTE_SIGNATURE → (chef accepte) → ACCEPTE
 *   EN_ATTENTE_SIGNATURE → (chef refuse)  → REFUSE + nouveau ContratTravail généré (tentative++)
 */
@Entity
@Table(name = "contrats_travail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContratTravail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ── Signalement concerné ── */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "signalement_id", nullable = false)
    private Signalement signalement;

    /* ── Équipe assignée ── */
    @Column(nullable = false, length = 60)
    private String equipeCode;          // ex: "voirie"

    @Column(nullable = false, length = 120)
    private String equipeLabel;         // ex: "Équipe Voirie"

    /* ── Chef d'équipe assigné (ID venant du module équipe) ── */
    @Column(length = 60)
    private String chefEquipeId;        // null jusqu'à résolution du module équipe

    /* ── Nom complet du chef d'équipe (pré-résolu à la création) ── */
    @Column(length = 120)
    private String chefEquipeNom;       // ex: "Mohamed Ben Ali"

    /* ── Statut ── */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutContrat statut = StatutContrat.EN_ATTENTE_SIGNATURE;

    /* ── Signature numérique (base64 de l'image canvas) ── */
    @Column(columnDefinition = "LONGTEXT")
    private String signatureBase64;

    /* ── Refus ── */
    @Column(length = 500)
    private String motifRefus;

    /* ── Numéro de tentative (1ère équipe, 2ème, ...) ── */
    @Builder.Default
    private Integer tentative = 1;

    /* ── Référence au contrat parent en cas de réaffectation ── */
    @Column(name = "contrat_parent_id")
    private Long contratParentId;

    /* ── Délai estimé (copié depuis l'analyse IA) ── */
    private Double delaiEstimeHeures;

    /* ── Numéro de contrat unique (lisible) ── */
    @Column(unique = true, length = 30)
    private String numeroContrat;

    /* ── Dates ── */
    @CreationTimestamp
    @Column(updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateCreation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateReponse;
}
