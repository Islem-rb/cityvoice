package tn.cityvoice.evenementservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.request.BudgetPredictRequest;
import tn.cityvoice.evenementservice.dto.response.BudgetPredictResponse;
import tn.cityvoice.evenementservice.service.BudgetPredictionService;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class BudgetPredictionController {

    private final BudgetPredictionService budgetService;

    @PostMapping("/predict")
    public ResponseEntity<BudgetPredictResponse> predictBudget(
            @RequestBody BudgetPredictRequest request) {
        return ResponseEntity.ok(budgetService.predire(request));
    }
}