package tn.cityvoice.evenementservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.request.TraductionRequest;
import tn.cityvoice.evenementservice.dto.response.TraductionResponse;
import tn.cityvoice.evenementservice.service.TraductionService;

@RestController
@RequestMapping("/api/traduction")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class TraductionController {

    private final TraductionService traductionService;

    @PostMapping
    public ResponseEntity<TraductionResponse> traduire(
            @RequestBody TraductionRequest req) {
        return ResponseEntity.ok(
                traductionService.traduire(req)
        );
    }
}