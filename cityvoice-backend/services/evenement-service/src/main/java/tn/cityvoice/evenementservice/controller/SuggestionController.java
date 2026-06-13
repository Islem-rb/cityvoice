package tn.cityvoice.evenementservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.request.SuggestionRequest;
import tn.cityvoice.evenementservice.dto.response.SuggestionAnalyseResponse;
import tn.cityvoice.evenementservice.dto.response.SuggestionResponse;
import tn.cityvoice.evenementservice.service.SuggestionService;

import java.util.List;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @PostMapping
    public ResponseEntity<SuggestionResponse> soumettre(
            @Valid @RequestBody SuggestionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suggestionService.soumettreSuggestion(req));
    }

    @GetMapping
    public ResponseEntity<List<SuggestionResponse>> lister() {
        return ResponseEntity.ok(suggestionService.listerToutes());
    }

    @GetMapping("/statut")
    public ResponseEntity<List<SuggestionResponse>> listerParStatut(
            @RequestParam(defaultValue = "SOUMISE") String statut) {
        return ResponseEntity.ok(suggestionService.listerParStatut(statut));
    }

    @GetMapping("/citoyen/{citoyenId}")
    public ResponseEntity<List<SuggestionResponse>> listerParCitoyen(
            @PathVariable String citoyenId) {
        return ResponseEntity.ok(suggestionService.listerParCitoyen(citoyenId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuggestionResponse> modifier(
            @PathVariable Long id,
            @Valid @RequestBody SuggestionRequest req) {
        return ResponseEntity.ok(suggestionService.modifierSuggestion(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        suggestionService.supprimerSuggestion(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/traiter")
    public ResponseEntity<SuggestionResponse> traiter(
            @PathVariable Long id,
            @RequestParam String statut,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String commentaire = body != null ? body.getOrDefault("commentaire", "") : "";
        return ResponseEntity.ok(
                suggestionService.traiterSuggestion(id, statut, commentaire)
        );
    }

    @PostMapping("/{id}/analyser")
    public ResponseEntity<SuggestionAnalyseResponse> analyser(
            @PathVariable Long id) {
        return ResponseEntity.ok(suggestionService.analyserAvecAI(id));
    }
    // ── Générer justification IA ───────────────────────
    @GetMapping("/{id}/justification")
    public ResponseEntity<java.util.Map<String, String>> justification(
            @PathVariable Long id,
            @RequestParam String statut) {
        String texte = suggestionService.genererJustification(id, statut);
        return ResponseEntity.ok(
                java.util.Map.of("justification", texte, "statut", statut)
        );
    }
}