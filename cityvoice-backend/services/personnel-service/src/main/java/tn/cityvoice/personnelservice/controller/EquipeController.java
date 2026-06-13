package tn.cityvoice.personnelservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.personnelservice.entity.Equipe;
import tn.cityvoice.personnelservice.entity.Etat;
import tn.cityvoice.personnelservice.entity.Fonction;
import tn.cityvoice.personnelservice.entity.MembreEquipe;
import tn.cityvoice.personnelservice.service.IEquipe;
import tn.cityvoice.personnelservice.service.IEquipeImp;
import tn.cityvoice.personnelservice.service.IMembreEquipeImp;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personnel/equipe")
@RequiredArgsConstructor
public class EquipeController {
    private final IEquipeImp equipeImp;

    @GetMapping("/get")
    public ResponseEntity<List<Equipe>> getAll() {
        return ResponseEntity.ok(equipeImp.getAllEquipes());
    }
    @GetMapping("/{id}")
    public ResponseEntity<Equipe> getEquipeById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(equipeImp.getEquipeById(id));
    }
    @GetMapping("/nom/{nom}")
    public ResponseEntity<Equipe> getEquipeByNom(@PathVariable("nom") String nom) {
        return ResponseEntity.ok(equipeImp.getEquipeByNom(nom));
    }
    @GetMapping("/specialite/{specialite}")
    public ResponseEntity<List<Equipe>> getEquipesBySpecialite(@PathVariable("specialite") String specialite) {
        return ResponseEntity.ok(equipeImp.getEquipesBySpecialite(specialite));
    }
    // EquipeController.java
    @PostMapping("/add")
    public ResponseEntity<Equipe> addEquipe(@RequestBody Equipe equipe) {
        Equipe saved = equipeImp.addEquipe(equipe); // doit retourner l'Equipe sauvegardée
        return ResponseEntity.ok(saved);            // retourne l'objet complet avec l'ID
    }
    @PutMapping("/{id}")
    public ResponseEntity<String> updateEquipe(@PathVariable("id") UUID id, @RequestBody Equipe equipe) {
        equipeImp.updateEquipe(id, equipe);
        return ResponseEntity.ok("Equipe mise à jour");
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEquipe(@PathVariable("id") UUID id) {
        equipeImp.deleteEquipe(id);
        return ResponseEntity.ok("Equipe supprimée");
    }
    @PutMapping("/{id}/{fonction}")
    public ResponseEntity<Void> updateFonction(
            @PathVariable("id") UUID id,
            @PathVariable("fonction") Etat fonction) {

        equipeImp.updateEquipeStatus(id, fonction);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/{id}/membre")
    public ResponseEntity<String> addMembreAEquipe(
            @PathVariable("id") UUID id,
            @RequestBody MembreEquipe membreEquipe) {

        try {
            equipeImp.addMembreAEquipe(id, membreEquipe);
            return ResponseEntity.ok("Membre ajouté avec succès");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @DeleteMapping("/{equipeId}/membre/{membreId}")
    public ResponseEntity<String> removeMembreFromEquipe(
            @PathVariable("equipeId") UUID equipeId,
            @PathVariable("membreId") UUID membreId) {
        equipeImp.removeMembreFromEquipe(equipeId, membreId);
        return ResponseEntity.ok("Membre supprimé");
    }
    @GetMapping("/{id}/has-fonction/{fonction}")
    public boolean hasFonction(@PathVariable("id") UUID id,
                               @PathVariable("fonction") Fonction fonction) {

        Equipe equipe = equipeImp.getEquipeById(id);
        return equipeImp.hasFonction(equipe, fonction);
    }




}
