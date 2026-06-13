package tn.cityvoice.personnelservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.function.EntityResponse;
import tn.cityvoice.personnelservice.entity.CandidatureEquipe;
import tn.cityvoice.personnelservice.entity.CvUser;
import tn.cityvoice.personnelservice.service.ICandidatureEquipeImp;
import tn.cityvoice.personnelservice.service.ICvUser;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personnel/candidature")
@RequiredArgsConstructor



public class CandidatureEquipeController {
    private final ICandidatureEquipeImp candidatureEquipeImp;
    private final ICvUser cvUserImp;

    @GetMapping("/statut/{statut}")
    public ResponseEntity<CandidatureEquipe> getByStatut(@PathVariable("statut") String statut) {
        return ResponseEntity.ok(candidatureEquipeImp.getCandidatureEquipeBystatut(statut));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        candidatureEquipeImp.deleteCandidatureEquipe(id);
        return ResponseEntity.noContent().build();
    }
    @PutMapping("/{id}")
    public ResponseEntity<CandidatureEquipe> updateCandidature(
            @PathVariable("id") UUID id,
            @RequestBody CandidatureEquipe candidature) {

        candidatureEquipeImp.updateCandidatureEquipe(id, candidature);

        // 🔥 récupérer l'objet après modification
        CandidatureEquipe updated = candidatureEquipeImp.getCandidatureEquipe(id);

        return ResponseEntity.ok(updated);
    }
    @GetMapping("/get")
    public ResponseEntity<List<CandidatureEquipe>> getAll() {
        return ResponseEntity.ok(candidatureEquipeImp.getAllCandidatureEquipes());
    }
    @PostMapping("/add")
    public ResponseEntity<CandidatureEquipe> addCandidature(@RequestBody CandidatureEquipe candidature) {
        CandidatureEquipe saved = candidatureEquipeImp.addCandidatureEquipe(candidature);
        return ResponseEntity.status(201).body(saved);
    }
    @GetMapping("/{id}")
    public ResponseEntity<CandidatureEquipe> getById(@PathVariable("id") UUID id) {
        CandidatureEquipe candidature = candidatureEquipeImp.getCandidatureEquipe(id);
        return ResponseEntity.ok(candidature);
    }
    @GetMapping("/{id}/specialite")
    public ResponseEntity<String> getNomEquipe(@PathVariable("id") UUID id) {
        CandidatureEquipe candidature = candidatureEquipeImp.getCandidatureEquipe(id);
        if (candidature == null) {
            return ResponseEntity.notFound().build();
        }

        if (candidature.getEquipe() == null) {
            return ResponseEntity.ok("Aucune équipe");
        }
        return ResponseEntity.ok(candidature.getEquipe().getName());




    }
    @PutMapping("/affecter/{candidatureId}/{equipeId}")
    public CandidatureEquipe affecterCandidature(
            @PathVariable("candidatureId") UUID candidatureId,
            @PathVariable("equipeId") UUID equipeId) {

        return candidatureEquipeImp.affecterAEquipe(candidatureId, equipeId);
    }
    @GetMapping("/candidature/{id}/cvs")
    public ResponseEntity<List<CvUser>> getCvs(@PathVariable UUID id) {

        return ResponseEntity.ok(cvUserImp.getAllCVsByCandidature(id));
    }
    @PostMapping("/add/{equipeId}")
    public CandidatureEquipe add(@RequestBody CandidatureEquipe c,
                                 @PathVariable("equipeId") UUID equipeId) {
        return candidatureEquipeImp.addCandidature(c, equipeId);
    }
    @GetMapping("/equipe/{equipeId}/fonction/{fonction}")
    public ResponseEntity<CandidatureEquipe> getByEquipeAndFonction(
            @PathVariable("equipeId") UUID equipeId,
            @PathVariable("fonction") String fonction) {

        CandidatureEquipe c = candidatureEquipeImp
                .getByEquipeAndFonction(equipeId, fonction);

        return ResponseEntity.ok(c);
    }









}



