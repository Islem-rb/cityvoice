package tn.cityvoice.evenementservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tn.cityvoice.evenementservice.enums.StatutEvenement;
import tn.cityvoice.evenementservice.enums.TypeEvenement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evenements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evenement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private TypeEvenement type;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StatutEvenement statut = StatutEvenement.BROUILLON;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String lieu;
    private Integer capaciteMax;
    private Double latitude;
    private Double longitude;

    @Builder.Default
    private Boolean estPayant = false;

    @Column(precision = 10, scale = 3)
    private BigDecimal prix;

    private Long organisateurId;
    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @OneToMany(mappedBy = "evenement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "evenement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EvenementSponsor> evenementSponsors = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "google_calendar_event_id")
    private String googleCalendarEventId;
    // Lieu détaillé
    private String typeLieu;   // SALLE, PLEIN_AIR, HOTEL, UNIVERSITE, MUNICIPALITE
    private String zone;       // LAC, MARSA, CENTRE_VILLE, BANLIEUE, AUTRE

    // Contexte médiatique
    @Builder.Default
    private Boolean mediaPrevu = false;

    @Builder.Default
    private Boolean streamingPrevu = false;

    // Budget
    @Column(precision = 10, scale = 3)
    private BigDecimal budgetEvenement;

    @Column(precision = 10, scale = 3)
    private BigDecimal budgetReel;
    public boolean estComplet() {
        return capaciteMax != null && participants.size() >= capaciteMax;
    }
}