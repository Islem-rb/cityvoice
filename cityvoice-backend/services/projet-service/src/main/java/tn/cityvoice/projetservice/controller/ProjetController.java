package tn.cityvoice.projetservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.projetservice.entity.*;
import tn.cityvoice.projetservice.entity.enums.StatutProjet;
import tn.cityvoice.projetservice.service.ProjetService;
import java.util.List;

@RestController
@RequestMapping("/api/projets")
@RequiredArgsConstructor
public class ProjetController {

    private final ProjetService projetService;

    @GetMapping
    public List<Projet> getAll() {
        return projetService.getAll();
    }

    @GetMapping("/{id}")
    public Projet getById(@PathVariable("id") Long id) {
        return projetService.getById(id);
    }

    @GetMapping("/statut/{statut}")
    public List<Projet> getByStatut(
            @PathVariable("statut") StatutProjet statut) {
        return projetService.getByStatut(statut);
    }

    @GetMapping("/categorie/{categorie}")
    public List<Projet> getByCategorie(
            @PathVariable("categorie") String categorie) {
        return projetService.getByCategorie(categorie);
    }

    @PostMapping
    public ResponseEntity<Projet> create(@RequestBody Projet projet) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projetService.create(projet));
    }

    @PutMapping("/{id}")
    public Projet update(
            @PathVariable("id") Long id,
            @RequestBody Projet projet) {
        return projetService.update(id, projet);
    }

    @PatchMapping("/{id}/statut")
    public Projet updateStatut(
            @PathVariable("id") Long id,
            @RequestParam("statut") StatutProjet statut) {
        return projetService.updateStatut(id, statut);
    }

    @PostMapping("/{id}/vote")
    public Projet vote(
            @PathVariable("id") Long id,
            @RequestBody VoteProjet vote) {
        return projetService.vote(id, vote);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        projetService.delete(id);
        return ResponseEntity.noContent().build();
    }

}