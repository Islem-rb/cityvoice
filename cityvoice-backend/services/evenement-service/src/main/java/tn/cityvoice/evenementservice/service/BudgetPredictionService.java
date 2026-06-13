package tn.cityvoice.evenementservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tn.cityvoice.evenementservice.dto.request.BudgetPredictRequest;
import tn.cityvoice.evenementservice.dto.response.BudgetPredictResponse;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class BudgetPredictionService {

    private final WebClient localClient = WebClient.builder().build();

    public BudgetPredictResponse predire(BudgetPredictRequest req) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type_evenement",  req.getTypeEvenement());
            body.put("capacite_max",    req.getCapaciteMax());
            body.put("type_lieu",       req.getTypeLieu()       != null ? req.getTypeLieu() : "SALLE");
            body.put("zone",            req.getZone()           != null ? req.getZone() : "CENTRE_VILLE");
            body.put("media_prevu",     req.getMediaPrevu()     != null ? req.getMediaPrevu() : 0);
            body.put("streaming_prevu", req.getStreamingPrevu() != null ? req.getStreamingPrevu() : 0);
            body.put("est_payant",      req.getEstPayant()      != null ? req.getEstPayant() : 0);
            body.put("prix_billet",     req.getPrixBillet()     != null ? req.getPrixBillet() : 0);
            body.put("duree_heures",    req.getDureeHeures()    != null ? req.getDureeHeures() : 4);
            body.put("date_debut",      req.getDateDebut()      != null ? req.getDateDebut() : "");

            return localClient.post()
                    .uri("http://localhost:5000/api/evenements/predict-budget")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(BudgetPredictResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("❌ Erreur prédiction budget: {}", e.getMessage());
            throw new RuntimeException("Service ML indisponible");
        }
    }
}