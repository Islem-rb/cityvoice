package tn.cityvoice.evenementservice.dto.response;

import lombok.*;
import tn.cityvoice.evenementservice.enums.StatutEvenement;
import tn.cityvoice.evenementservice.enums.TypeEvenement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvenementResponse {

    private Long id;
    private String titre;
    private String description;
    private TypeEvenement type;
    private StatutEvenement statut;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String lieu;
    private Integer capaciteMax;
    private Integer nbInscrits;
    private Boolean estPayant;
    private BigDecimal prix;
    private Long organisateurId;
    private String imageUrl;
    private LocalDateTime createdAt;
    private Double latitude;
    private Double longitude;
    private String typeLieu;
    private String zone;
    private Boolean mediaPrevu;
    private Boolean streamingPrevu;
    private BigDecimal budgetEvenement;
    private BigDecimal budgetReel;
    // Calculé
    public boolean isComplet() {
        return capaciteMax != null && nbInscrits >= capaciteMax;
    }

    public Integer placesRestantes() {
        if (capaciteMax == null) return null;
        return capaciteMax - nbInscrits;
    }
}