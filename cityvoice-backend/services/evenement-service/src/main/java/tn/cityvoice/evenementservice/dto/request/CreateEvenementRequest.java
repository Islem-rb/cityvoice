package tn.cityvoice.evenementservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import tn.cityvoice.evenementservice.enums.TypeEvenement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEvenementRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String titre;

    private String description;

    @NotNull(message = "Le type est obligatoire")
    private TypeEvenement type;

    @NotNull(message = "La date de début est obligatoire")
    @Future(message = "La date doit être dans le futur")
    private LocalDateTime dateDebut;

    private LocalDateTime dateFin;

    @NotBlank(message = "Le lieu est obligatoire")
    private String lieu;

    @Min(value = 1, message = "La capacité doit être au moins 1")
    private Integer capaciteMax;

    @NotNull
    private Boolean estPayant = false;

    @DecimalMin(value = "0.0", message = "Le prix doit être positif")
    private BigDecimal prix; // null si gratuit

    @NotNull(message = "L'organisateur est obligatoire")
    private Long organisateurId;

    private String imageUrl;
    private Double latitude;
    private Double longitude;
    // ── Lieu détaillé ─────────────────────────────────
    @Pattern(
            regexp = "SALLE|PLEIN_AIR|HOTEL|UNIVERSITE|MUNICIPALITE",
            message = "Type de lieu invalide"
    )
    private String typeLieu;
    //^$|
    @Pattern(
            regexp = "LAC|MARSA|CENTRE_VILLE|BANLIEUE|SFAX_CENTRE|SOUSSE_CENTRE|AUTRE",
            message = "Zone géographique invalide"
    )
    private String zone;

    // ── Contexte médiatique ───────────────────────────
    private Boolean mediaPrevu = false;
    private Boolean streamingPrevu = false;

    // ── Budget ────────────────────────────────────────
    @DecimalMin(value = "100.0", message = "Le budget minimum est 100 TND")
    @DecimalMax(value = "1000000.0", message = "Le budget maximum est 1 000 000 TND")
    private BigDecimal budgetEvenement;

    @DecimalMin(value = "0.0", message = "Le budget réel ne peut pas être négatif")
    private BigDecimal budgetReel;
}