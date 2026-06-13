package tn.cityvoice.evenementservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.response.EvenementResponse;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.repository.EvenementRepository;
import tn.cityvoice.evenementservice.service.CitoyenInteretService;
import tn.cityvoice.evenementservice.service.EvenementService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interets")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class CitoyenInteretController {

    private final CitoyenInteretService interetService;
    private final EvenementRepository evenementRepository;
    private final EvenementService evenementService;

    // Toggle intérêt
    @PostMapping("/{citoyenId}/{evenementId}")
    public ResponseEntity<Map<String, Boolean>> toggle(
            @PathVariable String citoyenId,
            @PathVariable Long evenementId) {
        boolean ajoute = interetService.toggleInteret(citoyenId, evenementId);
        return ResponseEntity.ok(Map.of("interesse", ajoute));
    }

    // Get IDs likés
    @GetMapping("/{citoyenId}")
    public ResponseEntity<List<Long>> getInterets(
            @PathVariable String citoyenId) {
        return ResponseEntity.ok(interetService.getInterets(citoyenId));
    }

    // Get recommandations
    @GetMapping("/{citoyenId}/recommandations")
    public ResponseEntity<List<EvenementResponse>> getRecommandations(
            @PathVariable String citoyenId) {
        log.info("→ Recommandations pour : {}", citoyenId);

        List<Long> ids = interetService.getRecommandations(citoyenId);
        log.info("→ IDs recommandés : {}", ids);

        List<EvenementResponse> recommandations = ids.stream()
                .map(id -> {
                    try {
                        return evenementService.getById(id);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(ev -> ev != null)
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(recommandations);
    }
}