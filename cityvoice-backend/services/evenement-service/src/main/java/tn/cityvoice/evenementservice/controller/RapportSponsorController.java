package tn.cityvoice.evenementservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.entity.RapportSponsor;
import tn.cityvoice.evenementservice.service.RapportSponsorService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sponsors/rapport")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RapportSponsorController {

    private final RapportSponsorService rapportService;

    // Générer manuellement
    @PostMapping("/generer")
    public ResponseEntity<RapportSponsor> generer() {
        return ResponseEntity.ok(rapportService.genererRapport());
    }

    // Historique
    @GetMapping("/historique")
    public ResponseEntity<List<RapportSponsor>> historique() {
        return ResponseEntity.ok(rapportService.getHistorique());
    }

    // Dernier rapport
    @GetMapping("/dernier")
    public ResponseEntity<?> dernier() {
        return rapportService.getDernier()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}