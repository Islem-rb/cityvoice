package tn.cityvoice.evenementservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.request.SponsorRequest;
import tn.cityvoice.evenementservice.dto.response.SponsorResponse;
import tn.cityvoice.evenementservice.service.SponsorService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class SponsorController {

    private final SponsorService sponsorService;

    // ─── CRUD Sponsors ────────────────────────────────────────
    @PostMapping("/api/sponsors")
    public ResponseEntity<SponsorResponse> creer(
            @Valid @RequestBody SponsorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sponsorService.creerSponsor(req));
    }

    @GetMapping("/api/sponsors")
    public ResponseEntity<List<SponsorResponse>> listerTous() {
        return ResponseEntity.ok(sponsorService.listerTous());
    }

    @PutMapping("/api/sponsors/{id}")
    public ResponseEntity<SponsorResponse> modifier(
            @PathVariable Long id,
            @Valid @RequestBody SponsorRequest req) {
        return ResponseEntity.ok(sponsorService.modifierSponsor(id, req));
    }

    @DeleteMapping("/api/sponsors/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        sponsorService.supprimerSponsor(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Association Sponsor ↔ Événement ──────────────────────
    @PostMapping("/api/sponsors/{sponsorId}/evenements/{evenementId}")
    public ResponseEntity<SponsorResponse> associer(
            @PathVariable Long sponsorId,
            @PathVariable Long evenementId,
            @RequestParam(required = false) String niveau,
            @RequestParam(required = false) BigDecimal montant) {
        return ResponseEntity.ok(
                sponsorService.associerAEvenement(sponsorId, evenementId, niveau, montant));
    }

    @DeleteMapping("/api/sponsors/{sponsorId}/evenements/{evenementId}")
    public ResponseEntity<Void> dissocier(
            @PathVariable Long sponsorId,
            @PathVariable Long evenementId) {
        sponsorService.dissocierDEvenement(sponsorId, evenementId);
        return ResponseEntity.noContent().build();
    }

    // ─── Sponsors par événement ───────────────────────────────
    @GetMapping("/api/evenements/{evenementId}/sponsors")
    public ResponseEntity<List<SponsorResponse>> listerParEvenement(
            @PathVariable Long evenementId) {
        return ResponseEntity.ok(sponsorService.listerParEvenement(evenementId));
    }
}