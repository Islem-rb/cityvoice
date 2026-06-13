package tn.cityvoice.evenementservice.dto.request;
import lombok.Data;

@Data
public class BudgetPredictRequest {
    private String typeEvenement;
    private Integer capaciteMax;
    private String typeLieu;
    private String zone;
    private Integer mediaPrevu;
    private Integer streamingPrevu;
    private Integer estPayant;
    private Double prixBillet;
    private Integer dureeHeures;
    private String dateDebut;
}