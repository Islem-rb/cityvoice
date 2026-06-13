package tn.cityvoice.signalement.dto;

import tn.cityvoice.signalement.enums.Priorite;
import tn.cityvoice.signalement.enums.TypeSignalement;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SignalementRequest {

    @NotNull(message = "Le type est obligatoire")
    private TypeSignalement type;

    @NotBlank(message = "La description est obligatoire")
    @Size(min = 10, max = 1000, message = "Description entre 10 et 1000 caractères")
    private String description;

    @NotNull(message = "La latitude est obligatoire")
    @DecimalMin(value = "30.0") @DecimalMax(value = "38.0")
    private Double latitude;

    @NotNull(message = "La longitude est obligatoire")
    @DecimalMin(value = "7.0") @DecimalMax(value = "13.0")
    private Double longitude;

    @Size(max = 500)
    private String adresse;

    private Priorite prioriteCitoyen = Priorite.MOYENNE;

    private Boolean estAnonyme = false;

    // Image en base64 — envoyée au service IA pour analyse
    private String imageBase64;

    // Identifiant de session vocale (rempli automatiquement pour les signalements par voix)
    private String voiceSessionId;
}
