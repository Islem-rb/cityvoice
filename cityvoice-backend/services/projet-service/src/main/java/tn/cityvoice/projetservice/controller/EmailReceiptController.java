package tn.cityvoice.projetservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.projetservice.service.EmailReceiptService;

import java.util.Map;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class EmailReceiptController {

    private final EmailReceiptService emailReceiptService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> send(
            @RequestBody Map<String, Object> req) {

        String toEmail    = req.getOrDefault("email",    "").toString();
        String nom        = req.getOrDefault("nom",      "Citoyen").toString();
        String projet     = req.getOrDefault("projet",   "Projet urbain").toString();
        float  montant    = Float.parseFloat(req.getOrDefault("montant", "0").toString());
        int    points     = Integer.parseInt(req.getOrDefault("points",  "0").toString());
        String reference  = req.getOrDefault("reference","N/A").toString();
        String methode    = req.getOrDefault("methode",  "Stripe").toString();

        if (toEmail.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email requis"));
        }

        emailReceiptService.sendReceipt(
                toEmail, nom, projet, montant, points, reference, methode
        );

        return ResponseEntity.ok(Map.of("status", "sent"));
    }
}