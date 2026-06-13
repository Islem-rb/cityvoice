package tn.cityvoice.evenementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tn.cityvoice.evenementservice.dto.request.SponsorPredictRequest;
import tn.cityvoice.evenementservice.dto.response.SponsorPredictResponse;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SponsorPredictionService {

    private final WebClient localClient = WebClient.builder().build();
    public SponsorPredictResponse predire(SponsorPredictRequest req) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type_evenement",   req.getTypeEvenement());
            body.put("capacite_max",     req.getCapaciteMax());
            body.put("lieu",             req.getLieu());
            body.put("budget_evenement", req.getBudgetEvenement());
            body.put("type_lieu",        req.getTypeLieu()      != null ? req.getTypeLieu() : "SALLE");
            body.put("zone",             req.getZone()          != null ? req.getZone() : "CENTRE_VILLE");
            body.put("media_prevu",      req.getMediaPrevu()    != null && req.getMediaPrevu() ? 1 : 0);
            body.put("streaming_prevu",  req.getStreamingPrevu() != null && req.getStreamingPrevu() ? 1 : 0);
            body.put("date_debut",       req.getDateDebut()     != null ? req.getDateDebut() : "");
            body.put("duree_heures",     req.getDureeHeures()   != null ? req.getDureeHeures() : 4);

            return localClient.post()
                    .uri("http://localhost:5000/api/sponsors/predict")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(SponsorPredictResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("❌ Erreur prédiction sponsor: {}", e.getMessage());
            throw new RuntimeException("Service ML indisponible");
        }
    }
}