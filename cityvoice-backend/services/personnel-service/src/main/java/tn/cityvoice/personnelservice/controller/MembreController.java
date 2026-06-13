package tn.cityvoice.personnelservice.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.personnelservice.entity.Fonction;
import tn.cityvoice.personnelservice.entity.MembreEquipe;
import tn.cityvoice.personnelservice.service.IMembreEquipeImp;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/personnel/membres")
@RequiredArgsConstructor
public class MembreController {
    private final IMembreEquipeImp membreEquipeImp;

    @GetMapping("/{id}")
    public ResponseEntity<MembreEquipe> getMembre(@PathVariable("id") UUID id) {
        MembreEquipe membre = membreEquipeImp.getMembreEquipe(id);
        return ResponseEntity.ok(membre);
    }
    @GetMapping("/get")
    public ResponseEntity<List<MembreEquipe>> getAll() {
        return ResponseEntity.ok(membreEquipeImp.getAllMembreEquipe());
    }
    @PostMapping("/add")
    public ResponseEntity<MembreEquipe> addMembre(@RequestBody MembreEquipe membre) {
        membreEquipeImp.addMembreEquipe(membre);
        return ResponseEntity.status(HttpStatus.CREATED).body(membre);
    }
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateMembre(@PathVariable("id") UUID id,
                                             @RequestBody MembreEquipe membre) {
        membreEquipeImp.updateMembreEquipe(id, membre);
        return ResponseEntity.ok().build();
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMembre(@PathVariable("id") UUID id) {
        membreEquipeImp.deleteMembreEquipe(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/fonction/{fonction}")
    public ResponseEntity<List<MembreEquipe>> getByFonction(@PathVariable("fonction") Fonction fonction) {
        return ResponseEntity.ok(membreEquipeImp.getMembreEquipe(fonction));
    }
    @GetMapping("/nom/{nom}")
    public ResponseEntity<List<MembreEquipe>> getByNom(@PathVariable("nom") String nom) {
        return ResponseEntity.ok(membreEquipeImp.getAllMembreEquipeByNom(nom));
    }
    @PutMapping("/{id}/{fonction}")
    public ResponseEntity<Void> updateFonction(
            @PathVariable("id") UUID id,
            @PathVariable("fonction") Fonction fonction) {

        membreEquipeImp.updateFonction(id, fonction);
        return ResponseEntity.ok().build();
    }
















}
