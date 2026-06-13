package tn.cityvoice.evenementservice.dto.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionAnalyseResponse {
    private int scorePertinence;        // 0-100
    private String niveauImpact;        // FAIBLE / MOYEN / ÉLEVÉ
    private String estimationParticipation; // "50-100 personnes"
    private String recommandation;      // CRÉER / REJETER / ATTENDRE
    private String justificationFr;
    private String justificationEn;
    private String categorieImpact;     // Éducatif / Environnemental / etc.
}