package tn.cityvoice.projetservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.projetservice.entity.*;
import tn.cityvoice.projetservice.entity.enums.*;
import tn.cityvoice.projetservice.repository.*;
import tn.cityvoice.projetservice.service.EmailReceiptService;
import tn.cityvoice.projetservice.service.SmsService;
import tn.cityvoice.projetservice.service.StripeService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeController {

    private final StripeService                 stripeService;
    private final PaiementRepository            paiementRepo;
    private final CollecteFinancementRepository collecteRepo;
    private final SmsService                    smsService;
    private final EmailReceiptService emailReceiptService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${userservice.url:http://localhost:8081}")
    private String userServiceUrl;

    @PostMapping("/initiate")
    public ResponseEntity<Map<String, String>> initiate(
            @RequestBody Map<String, Object> req) {

        Long   collecteId  = Long.valueOf(req.get("collecteId").toString());
        Float  montant     = Float.valueOf(req.get("montant").toString());
        String userId      = req.getOrDefault("userId",      "").toString();
        String email       = req.getOrDefault("email",       "").toString();
        String phone       = req.getOrDefault("phone",       "").toString();
        String description = req.getOrDefault("description", "Projet urbain").toString();
        boolean anon = Boolean.parseBoolean(
                req.getOrDefault("anonymous", "false").toString()
        );

        CollecteFinancement collecte = collecteRepo.findById(collecteId)
                .orElseThrow(() -> new RuntimeException("Collecte introuvable"));

        if (collecte.getStatut() != StatutCollecte.ACTIVE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Collecte non active"));
        }

        Paiement paiement = Paiement.builder()
                .collecte(collecte)
                .userId(userId)
                .montant(montant)
                .anonymous(anon)
                .email(email)
                .phone(phone)
                .methode(MethodePaiement.PAIEMENT_EN_LIGNE)
                .statut(StatutPaiement.EN_ATTENTE)
                .build();
        paiement = paiementRepo.save(paiement);

        Map<String, String> result = stripeService.createCheckoutSession(
                montant, paiement.getId(), collecteId, description
        );

        if (result.isEmpty()) {
            paiementRepo.delete(paiement);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erreur Stripe"));
        }

        paiement.setReference("STRIPE-" + result.get("sessionId"));
        paiementRepo.save(paiement);

        return ResponseEntity.ok(Map.of(
                "checkoutUrl", result.get("checkoutUrl"),
                "paiementId",  paiement.getId().toString(),
                "sessionId",   result.get("sessionId")
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestBody Map<String, Object> req) {

        Long   paiementId = Long.valueOf(req.get("paiementId").toString());
        String sessionId  = req.get("sessionId").toString();

        log.info("VERIFY called: paiementId={} sessionId={}", paiementId, sessionId);

        Paiement paiement = paiementRepo.findById(paiementId)
                .orElseThrow(() -> new RuntimeException("Paiement introuvable: " + paiementId));

        log.info("Paiement found: statut={} userId={}", paiement.getStatut(), paiement.getUserId());

        if (paiement.getStatut() == StatutPaiement.CONFIRME) {
            log.info("Already confirmed — returning success");
            return ResponseEntity.ok(Map.of(
                    "success", true, "alreadyConfirmed", true,
                    "montant", paiement.getMontant()
            ));
        }

        log.info("Calling stripeService.verifySession({})", sessionId);
        boolean paid = stripeService.verifySession(sessionId);
        log.info("Stripe says paid={}", paid);

        if (paid) {
            paiement.setStatut(StatutPaiement.CONFIRME);
            paiementRepo.save(paiement);

            CollecteFinancement collecte = paiement.getCollecte();
            collecte.setMontantCollecte(
                    collecte.getMontantCollecte() + paiement.getMontant()
            );
            if (collecte.getMontantCollecte() >= collecte.getMontantCible()) {
                collecte.setStatut(StatutCollecte.OBJECTIF_ATTEINT);
            }
            collecteRepo.save(collecte);

            int    points = calculatePoints(paiement.getMontant());
            String titre  = collecte.getProjet() != null
                    ? collecte.getProjet().getTitre() : "Projet urbain";

            addPointsToUser(paiement.getUserId(), points, titre);

            if (!Boolean.TRUE.equals(paiement.getAnonymous())) {
                String userEmail = paiement.getUserId();
                String userName  = EmailReceiptService.extractName(userEmail);
                emailReceiptService.sendReceipt(
                        userEmail, userName, titre,
                        paiement.getMontant(), points,
                        paiement.getReference() != null ? paiement.getReference() : "N/A",
                        "Stripe"
                );
                smsService.sendDonationSms(
                        paiement.getPhone(), titre,
                        paiement.getMontant(), points
                );
            }

            log.info("Payment confirmed: paiementId={} montant={} points={}",
                    paiementId, paiement.getMontant(), points);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "montant", paiement.getMontant(),
                    "points",  points,
                    "email",   paiement.getEmail() != null ? paiement.getEmail() : ""
            ));

        } else {
            log.warn("Stripe did NOT confirm payment for sessionId={}", sessionId);
            paiement.setStatut(StatutPaiement.ECHOUE);
            paiementRepo.save(paiement);
            return ResponseEntity.ok(Map.of("success", false));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig(
            @Value("${stripe.publishable-key}") String pubKey) {
        return ResponseEntity.ok(Map.of("publishableKey", pubKey));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Paiement>> getHistory(
            @RequestParam("userId") String userId) {
        return ResponseEntity.ok(
                paiementRepo.findByUserIdOrderByDateDesc(userId)
        );
    }

    private int calculatePoints(float montant) {
        if (montant >= 1000) return 100;
        if (montant >= 500)  return 50;
        if (montant >= 100)  return 20;
        return 5;
    }

    private void addPointsToUser(String userEmail, int points, String titre) {
        try {
            String url = userServiceUrl + "/api/users/by-email/points?email=" + userEmail;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of(
                    "points", points,
                    "description", "Don pour : " + titre
            ), headers);

            ResponseEntity<Map> resp = restTemplate.postForEntity(url, entity, Map.class);
            if (resp.getStatusCode() == HttpStatus.OK) {
                log.info("Points added successfully for email {}", userEmail);
            } else {
                log.warn("Points addition returned status: {}", resp.getStatusCode());
            }
        } catch (Exception e) {
            log.error("addPointsToUser FAILED for {}: {}", userEmail, e.getMessage(), e);
        }
    }
}