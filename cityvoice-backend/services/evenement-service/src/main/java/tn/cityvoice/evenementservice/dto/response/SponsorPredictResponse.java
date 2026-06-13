package tn.cityvoice.evenementservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class SponsorPredictResponse {

    @JsonProperty("niveau_recommande")
    private String niveauRecommande;

    @JsonProperty("montant_optimal")
    private Integer montantOptimal;

    @JsonProperty("probabilite_acceptation")
    private Integer probabiliteAcceptation;

    @JsonProperty("probabilites_par_niveau")
    private Map<String, Double> probabilitesParNiveau;

    @JsonProperty("explication")
    private String explication;

    @JsonProperty("email_demarche")
    private String emailDemarche;

    @JsonProperty("facteurs_cles")
    private Map<String, String> facteursCles;
}