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

import java.util.*;

@RestController
@RequestMapping("/api/konnect")
@RequiredArgsConstructor
@Slf4j
public class KonnectController {

    private final PaiementRepository            paiementRepo;
    private final CollecteFinancementRepository collecteRepo;
    private final SmsService                    smsService;
    private final EmailReceiptService emailReceiptService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${konnect.api-key:}")
    private String apiKey;

    @Value("${konnect.wallet-id:}")
    private String walletId;   // ← injected from properties, NOT from extractWalletId()

    @Value("${konnect.base-url:https://api.preprod.konnect.network/api/v2}")
    private String baseUrl;

    @Value("${konnect.webhook-url:http://localhost:8080/api/konnect/webhook}")
    private String webhookUrl;

    @Value("${konnect.success-url:http://localhost:4200/projets/payment/success}")
    private String successUrl;

    @Value("${konnect.fail-url:http://localhost:4200/projets/payment/fail}")
    private String failUrl;

    @Value("${userservice.url:http://localhost:8081}")
    private String userServiceUrl;

    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiate(
            @RequestBody Map<String, Object> req) {

        // ← Always log first so we see output even if exception thrown
        log.info("=== KONNECT INITIATE ===");
        log.info("apiKey length    : {}", apiKey.length());
        log.info("apiKey starts    : [{}]", apiKey.substring(0, Math.min(12, apiKey.length())));
        log.info("walletId         : [{}]", walletId);
        log.info("baseUrl          : {}", baseUrl);
        log.info("request          : {}", req);

        if (apiKey.isBlank()) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "konnect.api-key not set in application.properties"));
        }
        if (walletId.isBlank()) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "konnect.wallet-id not set in application.properties"));
        }

        try {
            Long    collecteId  = Long.valueOf(req.get("collecteId").toString());
            Float   montant     = Float.valueOf(req.get("montant").toString());
            String  userId      = req.getOrDefault("userId",      "").toString();
            String  phone       = req.getOrDefault("phone",       "").toString();
            String  description = req.getOrDefault("description", "Projet urbain").toString();
            boolean anon        = Boolean.parseBoolean(
                    req.getOrDefault("anonymous", "false").toString());

            CollecteFinancement collecte = collecteRepo.findById(collecteId)
                    .orElseThrow(() -> new RuntimeException("Collecte introuvable: " + collecteId));

            if (collecte.getStatut() != StatutCollecte.ACTIVE) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Collecte non active: " + collecte.getStatut()));
            }

            Paiement paiement = Paiement.builder()
                    .collecte(collecte)
                    .userId(userId)
                    .montant(montant)
                    .anonymous(anon)
                    .phone(phone)
                    .methode(MethodePaiement.PAIEMENT_EN_LIGNE)
                    .statut(StatutPaiement.EN_ATTENTE)
                    .build();
            paiement = paiementRepo.save(paiement);
            log.info("Paiement saved id={}", paiement.getId());

            // Konnect amount is in millimes (×1000)
            int millimes = Math.round(montant * 1000);
            log.info("Amount millimes={}", millimes);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receiverWalletId",       walletId);  // ← uses injected field
            body.put("token",                  "TND");
            body.put("amount",                 millimes);
            body.put("type",                   "immediate");
            body.put("description",            description);
            body.put("acceptedPaymentMethods", List.of("wallet", "bank_card", "e-DINAR"));
            body.put("lifespan",               30);
            body.put("checkoutForm",           true);
            body.put("addPaymentFeesToAmount", false);
            body.put("firstName",              "Citoyen");
            body.put("lastName",               "Madina");
            body.put("phoneNumber",            phone.isBlank() ? "00000000" : phone);
            body.put("silentWebhook",          false);
            body.put("webhook",                webhookUrl);
            body.put("successUrl",             successUrl + "?paiement=" + paiement.getId());
            body.put("failUrl",                failUrl);
            body.put("orderId",                "PAY-" + paiement.getId());

            log.info("Konnect body: {}", body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey.trim());  // ← ONLY x-api-key, no Basic auth

            String endpoint = baseUrl + "/payments/init-payment";
            log.info("Calling: {}", endpoint);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            log.info("Konnect status : {}", response.getStatusCode());
            log.info("Konnect body   : {}", response.getBody());

            if (response.getBody() != null) {
                Object payUrlObj = response.getBody().get("payUrl");
                Object payRefObj = response.getBody().get("paymentRef");
                String payUrl = payUrlObj != null ? String.valueOf(payUrlObj) : "";
                String payRef = payRefObj != null ? String.valueOf(payRefObj) : "";

                if (!payUrl.isBlank()) {
                    paiement.setReference("KONNECT-" + payRef);
                    paiementRepo.save(paiement);
                    return ResponseEntity.ok(Map.of(
                            "checkoutUrl", payUrl,
                            "paiementId",  paiement.getId().toString(),
                            "paymentRef",  payRef
                    ));
                }

                log.warn("No payUrl in response: {}", response.getBody());
            }

            paiementRepo.delete(paiement);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Pas d'URL dans la réponse Konnect",
                            "body",  String.valueOf(response.getBody())));

        } catch (Exception e) {
            log.error("Konnect EXCEPTION: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody Map<String, Object> payload) {
        log.info("Konnect webhook: {}", payload);
        try {
            Object payRefObj = payload.get("payment_ref");
            if (payRefObj == null) payRefObj = payload.get("paymentRef");
            String payRef = payRefObj != null ? String.valueOf(payRefObj) : "";

            Object statusObj = payload.get("payment_status");
            if (statusObj == null) statusObj = payload.get("status");
            String status = statusObj != null ? String.valueOf(statusObj) : "";

            log.info("Webhook ref={} status={}", payRef, status);
            if (payRef.isBlank()) return ResponseEntity.ok().build();

            Paiement paiement = paiementRepo.findAll().stream()
                    .filter(p -> p.getReference() != null && p.getReference().contains(payRef))
                    .findFirst().orElse(null);

            if (paiement == null) { log.warn("No paiement for ref {}", payRef); return ResponseEntity.ok().build(); }
            if (paiement.getStatut() == StatutPaiement.CONFIRME) return ResponseEntity.ok().build();

            if (List.of("completed","paid","success").contains(status.toLowerCase())) {
                confirmPayment(paiement);
            } else if (List.of("failed","cancelled").contains(status.toLowerCase())) {
                paiement.setStatut(StatutPaiement.ECHOUE);
                paiementRepo.save(paiement);
            }
        } catch (Exception e) {
            log.error("Webhook error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestBody Map<String, Object> req) {

        Long   paiementId = Long.valueOf(req.get("paiementId").toString());
        Object payRefObj  = req.get("paymentRef");
        String payRef     = payRefObj != null ? String.valueOf(payRefObj) : "";

        log.info("Konnect verify paiementId={} payRef={}", paiementId, payRef);

        Paiement paiement = paiementRepo.findById(paiementId)
                .orElseThrow(() -> new RuntimeException("Paiement introuvable"));

        if (paiement.getStatut() == StatutPaiement.CONFIRME) {
            return ResponseEntity.ok(Map.of(
                    "success", true, "alreadyConfirmed", true,
                    "montant", paiement.getMontant()
            ));

        }

        if (!payRef.isBlank()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("x-api-key", apiKey.trim());

                ResponseEntity<Map> resp = restTemplate.exchange(
                        baseUrl + "/payments/" + payRef,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                );
                log.info("Verify GET response: {}", resp.getBody());

                if (resp.getBody() != null) {
                    Map<?, ?> data = resp.getBody();
                    if (data.containsKey("payment")) data = (Map<?, ?>) data.get("payment");

                    Object st = data.get("status");
                    if (st == null) st = data.get("payment_status");
                    String status = st != null ? String.valueOf(st) : "";
                    log.info("Payment status from Konnect: {}", status);

                    if (List.of("completed","paid","success").contains(status.toLowerCase())) {
                        confirmPayment(paiement);
                        return ResponseEntity.ok(Map.of(
                                "success", true,
                                "montant", paiement.getMontant(),
                                "points",  calculatePoints(paiement.getMontant())
                        ));

                    }
                }
            } catch (Exception e) {
                log.error("Verify error: {}", e.getMessage(), e);
            }
        }
        return ResponseEntity.ok(Map.of("success", false));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Paiement>> getHistory(@RequestParam("userId") String userId) {
        return ResponseEntity.ok(paiementRepo.findByUserIdOrderByDateDesc(userId));
    }

    private void confirmPayment(Paiement paiement) {
        paiement.setStatut(StatutPaiement.CONFIRME);
        paiementRepo.save(paiement);

        CollecteFinancement collecte = paiement.getCollecte();
        collecte.setMontantCollecte(collecte.getMontantCollecte() + paiement.getMontant());
        if (collecte.getMontantCollecte() >= collecte.getMontantCible())
            collecte.setStatut(StatutCollecte.OBJECTIF_ATTEINT);
        collecteRepo.save(collecte);

        int    points = calculatePoints(paiement.getMontant());
        String titre  = collecte.getProjet() != null
                ? collecte.getProjet().getTitre() : "Projet urbain";

        addPointsToUser(paiement.getUserId(), points, titre);

        // ── Skip everything if anonymous ──────────────────────
        if (!Boolean.TRUE.equals(paiement.getAnonymous())) {
            String userEmail = paiement.getUserId();
            String userName  = EmailReceiptService.extractName(userEmail);

            // Email receipt with PDF
            emailReceiptService.sendReceipt(
                    userEmail, userName, titre,
                    paiement.getMontant(), points,
                    paiement.getReference() != null ? paiement.getReference() : "N/A",
                    "Konnect"
            );

            // SMS
            smsService.sendDonationSms(
                    paiement.getPhone(), titre,
                    paiement.getMontant(), points
            );
        }

        log.info("Confirmed: id={} montant={} pts={}",
                paiement.getId(), paiement.getMontant(), points);
    }

    private int calculatePoints(float montant) {
        if (montant >= 1000) return 100;
        if (montant >= 500)  return 50;
        if (montant >= 100)  return 20;
        return 5;
    }

    private void addPointsToUser(String userEmail, int points, String titre) {
        try {
            String encoded = java.net.URLEncoder.encode(userEmail, java.nio.charset.StandardCharsets.UTF_8);
            ResponseEntity<Map> userResp = restTemplate.getForEntity(
                    userServiceUrl + "/api/users/by-email?email=" + encoded, Map.class);
            if (userResp.getStatusCode() != HttpStatus.OK || userResp.getBody() == null) return;

            String uuid = userResp.getBody().get("id").toString();
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(
                    userServiceUrl + "/api/users/" + uuid + "/points",
                    new HttpEntity<>(Map.of("points", points, "description", "Don Konnect : " + titre), h),
                    Map.class);
            log.info("Points {} added to {}", points, userEmail);
        } catch (Exception e) {
            log.error("addPointsToUser failed: {}", e.getMessage());
        }
    }
}