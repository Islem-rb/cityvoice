package tn.cityvoice.signalement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse à un signalement créé depuis Jami.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JamiSignalementResponse {

    private Long signalementId;
    private String type;
    private String description;
    private String adresse;
    private String priorite;
    private String statut;
    private String sessionId;
}
