package tn.cityvoice.projetservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.projetservice.entity.Paiement;
import tn.cityvoice.projetservice.service.PaiementService;
import java.util.List;

@RestController
@RequestMapping("/api/collectes")
@RequiredArgsConstructor
public class PaiementController {

    private final PaiementService paiementService;

    @PostMapping("/{collecteId}/payer")
    public ResponseEntity<Paiement> pay(
            @PathVariable("collecteId") Long collecteId,
            @RequestBody Paiement paiement) {
        return ResponseEntity.ok(
                paiementService.pay(collecteId, paiement));
    }

    @GetMapping("/{collecteId}/paiements")
    public List<Paiement> getByCollecte(
            @PathVariable("collecteId") Long collecteId) {
        return paiementService.getByCollecte(collecteId);
    }

    @GetMapping("/user/{userId}")
    public List<Paiement> getByUser(
            @PathVariable("userId") String userId) {
        return paiementService.getByUser(userId);
    }
}