package tn.cityvoice.signalement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class AiAnalysisResponse {

    private String categorie;
    private String priorite;

    @JsonProperty("priorite_score")
    private Integer prioriteScore;

    private String equipe;

    @JsonProperty("equipe_label")
    private String equipeLabel;

    @JsonProperty("delai_heures")
    private Double delaiHeures;

    @JsonProperty("description_amelioree")
    private String descriptionAmelioree;

    private Map<String, Double> confidences;

    @JsonProperty("processing_ms")
    private Double processingMs;
}
