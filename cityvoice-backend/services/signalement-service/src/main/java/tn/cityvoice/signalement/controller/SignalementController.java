package tn.cityvoice.signalement.controller;

import tn.cityvoice.signalement.dto.SignalementRequest;
import tn.cityvoice.signalement.dto.StatutUpdateRequest;
import tn.cityvoice.signalement.entity.Signalement;
import tn.cityvoice.signalement.enums.StatutSignalement;
import tn.cityvoice.signalement.service.ISignalementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("/api/v1/signalements")
@RequiredArgsConstructor
@Slf4j
public class SignalementController {

    private final ISignalementService service;

    // ── ADMIN : Stats — AVANT /{id} pour éviter le conflit ───────────────────
    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        return service.getStats();
    }

    // ── PUBLIC : Liste / par statut ───────────────────────────────────────────
    @GetMapping
    public List<Signalement> getAll(
        @RequestParam(required = false) StatutSignalement statut
    ) {
        if (statut != null) return service.getByStatut(statut);
        return service.getAll();
    }

    // ── PUBLIC : Signalements proches (carte) — AVANT /{id} ──────────────────
    @GetMapping("/proximite")
    public List<Signalement> getProximite(
        @RequestParam double lat,
        @RequestParam double lng,
        @RequestParam(defaultValue = "5") double km
    ) {
        return service.getByProximite(lat, lng, km);
    }

    // ── CITOYEN : Mes signalements — AVANT /{id} ─────────────────────────────
    @GetMapping("/mes-signalements")
    public List<Signalement> getMesSignalements(
        @RequestHeader("X-User-Id") String citoyenId
    ) {
        return service.getMesSignalements(citoyenId);
    }

    // ── CITOYEN : Créer ───────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Signalement> create(
        @RequestBody @Valid SignalementRequest req,
        @RequestHeader("X-User-Id") String citoyenId
    ) {
        log.info("[POST] Nouveau signalement de citoyen #{}", citoyenId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(req, citoyenId));
    }

    // ── PUBLIC : Par ID — après toutes les routes fixes ──────────────────────
    @GetMapping("/{id}")
    public Signalement getById(@PathVariable Long id) {
        return service.getById(id);
    }

    // ── CITOYEN : Voter ───────────────────────────────────────────────────────
    @PostMapping("/{id}/vote")
    public Signalement voter(@PathVariable Long id) {
        return service.voter(id);
    }

    // ── ADMIN : Changer le statut ─────────────────────────────────────────────
    // ── ADMIN : Corriger la localisation (utile après écoute vocale) ──────────
    @PatchMapping("/{id}/localisation")
    public ResponseEntity<Signalement> updateLocalisation(
        @PathVariable Long id,
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-User-Id", defaultValue = "admin") String operateurId
    ) {
        log.info("[PATCH] Localisation #{} par #{}", id, operateurId);
        Signalement updated = service.updateLocalisation(id,
            body.get("latitude")  != null ? Double.parseDouble(body.get("latitude").toString())  : null,
            body.get("longitude") != null ? Double.parseDouble(body.get("longitude").toString()) : null,
            body.get("adresse")   != null ? body.get("adresse").toString() : null
        );
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/statut")
    public Signalement changerStatut(
        @PathVariable Long id,
        @RequestBody @Valid StatutUpdateRequest req,
        @RequestHeader("X-User-Id") String operateurId
    ) {
        log.info("[PATCH] Statut #{} → {} par #{}", id, req.getNouveauStatut(), operateurId);
        return service.changerStatut(id, req, operateurId);
    }

    /**
     * CHEF D'ÉQUIPE : Marquer comme résolu avec photo "après".
     * LLaVA compare la photo originale et la photo de résolution.
     * Retourne le signalement mis à jour + rapport de vérification IA.
     */
    @PostMapping("/{id}/resoudre")
    public ResponseEntity<Map<String, Object>> resoudreParChef(
        @PathVariable Long id,
        @RequestBody Map<String, String> body,
        @RequestHeader("X-User-Id") String chefId
    ) {
        String photoApres   = body.get("photoApres");
        String commentaire  = body.getOrDefault("commentaire", "");
        log.info("[RESOUDRE] Signalement #{} par chef={}", id, chefId);
        return ResponseEntity.ok(service.resoudreParChef(id, photoApres, commentaire, chefId));
    }

    // ── ADMIN : Supprimer ─────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @RequestHeader(value = "X-User-Role", defaultValue = "") String role
    ) {
        log.info("[DELETE] Signalement #{} — demandé par rôle={}", id, role);
        service.delete(id, role);
        return ResponseEntity.noContent().build();
    }

    // ── PUBLIC : Vérification doublon ─────────────────────────────────────────
    @GetMapping("/check-doublon")
    public ResponseEntity<?> checkDoublon(
        @RequestParam double lat,
        @RequestParam double lng,
        @RequestParam String type
    ) {
        List<Signalement> proches = service.getByProximite(lat, lng, 0.2);
        List<Signalement> doublons = proches.stream()
            .filter(s -> s.getType().name().equals(type))
            .filter(s -> !s.getStatut().name().equals("RESOLU"))
            .toList();

        if (doublons.isEmpty()) {
            return ResponseEntity.ok(Map.of("hasDoublon", false));
        }
        return ResponseEntity.ok(Map.of(
            "hasDoublon",  true,
            "signalement", doublons.get(0)
        ));
    }
}
