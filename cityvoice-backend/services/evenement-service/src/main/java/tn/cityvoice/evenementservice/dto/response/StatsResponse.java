package tn.cityvoice.evenementservice.dto.response;

import lombok.*;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    // KPIs principaux
    private Long totalEvenements;
    private Long evenementsPublies;
    private Long evenementsBrouillon;
    private Long evenementsAnnules;
    private Long totalInscrits;
    private Double totalRevenus;
    private Double tauxRemplissageMoyen;

    // Répartitions
    private Map<String, Long> parType;
    private Map<String, Long> parStatut;
    private Map<String, Long> parZone;
    private Map<String, Long> parTypeLieu;

    // Tendances
    private Map<String, Long> inscriptionsParMois;
    private Map<String, Long> evenementsParMois;

    // Top 5
    private java.util.List<EvenementResponse> top5Inscrits;

    // Cette semaine
    private java.util.List<EvenementResponse> evenementsCetteSemaine;
}