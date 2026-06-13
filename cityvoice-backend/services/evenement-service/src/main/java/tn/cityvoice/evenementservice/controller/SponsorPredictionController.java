package tn.cityvoice.evenementservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.request.SponsorPredictRequest;
import tn.cityvoice.evenementservice.dto.response.SponsorPredictResponse;
import tn.cityvoice.evenementservice.service.SponsorPredictionService;

@RestController
@RequestMapping("/api/sponsors")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class SponsorPredictionController {

    private final SponsorPredictionService predictionService;

    @PostMapping("/predict")
    public ResponseEntity<SponsorPredictResponse> predict(
            @RequestBody SponsorPredictRequest request) {
        return ResponseEntity.ok(predictionService.predire(request));
    }
}