package tn.cityvoice.evenementservice.dto.request;

import lombok.Data;

@Data
public class SponsorPredictRequest {
    private String typeEvenement;
    private Integer capaciteMax;
    private String lieu;
    private Double budgetEvenement;
    private String typeLieu;
    private String zone;
    private Boolean mediaPrevu;
    private Boolean streamingPrevu;
    private String dateDebut;
    private Integer dureeHeures;
}