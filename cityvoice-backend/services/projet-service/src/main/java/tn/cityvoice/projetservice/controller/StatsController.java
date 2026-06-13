package tn.cityvoice.projetservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.projetservice.entity.Paiement;
import tn.cityvoice.projetservice.entity.Projet;
import tn.cityvoice.projetservice.entity.enums.StatutPaiement;
import tn.cityvoice.projetservice.repository.PaiementRepository;
import tn.cityvoice.projetservice.repository.ProjetRepository;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ProjetRepository  projetRepo;
    private final PaiementRepository paiementRepo;

    private static final List<String> GOUVERNORATS = List.of(
            "Ariana","Béja","Ben Arous","Bizerte","Gabès",
            "Gafsa","Jendouba","Kairouan","Kasserine","Kébili",
            "Le Kef","Mahdia","La Manouba","Médenine","Monastir",
            "Nabeul","Sfax","Sidi Bouzid","Siliana","Sousse",
            "Tataouine","Tozeur","Tunis","Zaghouan"
    );

    /**
     * Stat 1 — Projects count per gouvernorat
     */
    @GetMapping("/by-gouvernorat")
    public ResponseEntity<List<Map<String, Object>>> byGouvernorat() {
        List<Projet> all = projetRepo.findAll();

        List<Map<String, Object>> result = GOUVERNORATS.stream().map(gov -> {
            long count = all.stream()
                    .filter(p -> gov.equalsIgnoreCase(p.getLocation()))
                    .count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gouvernorat", gov);
            m.put("count", count);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Stat 2 — Projects count per category
     */
    @GetMapping("/by-category")
    public ResponseEntity<List<Map<String, Object>>> byCategory(
            @RequestParam(value = "gouvernorat", required = false) String gouvernorat) {

        List<Projet> all = projetRepo.findAll();

        // Filter by gouvernorat if provided
        if (gouvernorat != null && !gouvernorat.isBlank()) {
            all = all.stream()
                    .filter(p -> gouvernorat.equalsIgnoreCase(p.getLocation()))
                    .collect(java.util.stream.Collectors.toList());
        }

        Map<String, Long> grouped = all.stream()
                .filter(p -> p.getCategorie() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        Projet::getCategorie, java.util.stream.Collectors.counting()
                ));

        List<Map<String, Object>> result = grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("categorie", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Stat 3 — Top 5 donators per gouvernorat
     * Groups confirmed payments by userId, filters projects by gouvernorat
     */
    @GetMapping("/top-donators")
    public ResponseEntity<List<Map<String, Object>>> topDonators(
            @RequestParam(value = "gouvernorat", required = false) String gouvernorat) {

        List<Paiement> confirmed = paiementRepo.findAll().stream()
                .filter(p -> p.getStatut() == StatutPaiement.CONFIRME)
                .collect(Collectors.toList());

        // Filter by gouvernorat if provided
        if (gouvernorat != null && !gouvernorat.isBlank()) {
            confirmed = confirmed.stream()
                    .filter(p -> {
                        if (p.getCollecte() == null) return false;
                        if (p.getCollecte().getProjet() == null) return false;
                        return gouvernorat.equalsIgnoreCase(
                                p.getCollecte().getProjet().getLocation()
                        );
                    })
                    .collect(Collectors.toList());
        }

        Map<String, Double> totals = new LinkedHashMap<>();
        for (Paiement p : confirmed) {
            String uid = p.getUserId() != null ? p.getUserId() : "Anonyme";
            if (Boolean.TRUE.equals(p.getAnonymous())) uid = "Anonyme";
            totals.merge(uid, (double) p.getMontant(), Double::sum);
        }

        List<Map<String, Object>> result = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    // Show only first part of email for privacy
                    String display = e.getKey();
                    if (display.contains("@") && !display.equals("Anonyme")) {
                        display = display.split("@")[0];
                    }
                    m.put("userId",  display);
                    m.put("total",   e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

}