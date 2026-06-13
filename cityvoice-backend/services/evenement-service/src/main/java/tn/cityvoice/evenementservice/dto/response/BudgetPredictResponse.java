package tn.cityvoice.evenementservice.dto.response;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class BudgetPredictResponse {
    @JsonProperty("budget_recommande")
    private Integer budgetRecommande;

    @JsonProperty("fourchette_min")
    private Integer fourchetteMin;

    @JsonProperty("fourchette_max")
    private Integer fourchetteMax;

    @JsonProperty("decomposition")
    private Map<String, Integer> decomposition;

    @JsonProperty("nb_sponsors_recommande")
    private Integer nbSponsorsRecommande;

    @JsonProperty("recette_estimee")
    private Integer recetteEstimee;

    @JsonProperty("budget_net")
    private Integer budgetNet;

    @JsonProperty("explication")
    private String explication;

    @JsonProperty("facteurs")
    private Map<String, Object> facteurs;
}