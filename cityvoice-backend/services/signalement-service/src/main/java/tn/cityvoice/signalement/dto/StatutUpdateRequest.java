package tn.cityvoice.signalement.dto;

import tn.cityvoice.signalement.enums.StatutSignalement;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatutUpdateRequest {

    @NotNull(message = "Le nouveau statut est obligatoire")
    private StatutSignalement nouveauStatut;

    private String commentaire;

    /** Assignation manuelle d'équipe (chemin sans IA) — optionnel */
    private String equipeIA;
    private String equipeIALabel;
}
