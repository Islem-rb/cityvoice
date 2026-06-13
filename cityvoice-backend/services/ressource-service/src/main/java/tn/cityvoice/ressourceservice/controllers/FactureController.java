// tn/cityvoice/ressourceservice/controllers/FactureController.java
package tn.cityvoice.ressourceservice.controllers;

import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import tn.cityvoice.ressourceservice.entity.Facture;
import tn.cityvoice.ressourceservice.services.FactureService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.ressourceservice.services.StripeService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/factures")
@CrossOrigin(origins = "http://localhost:4200")
public class FactureController {

    private final FactureService factureService;
    private final StripeService stripeService;

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;

    public FactureController(FactureService factureService, StripeService stripeService) {
        this.factureService = factureService;
        this.stripeService = stripeService;
    }

    @PostMapping
    public ResponseEntity<Facture> create(@RequestBody Facture facture) {
        System.out.println("📄 Création facture: " + facture.getDescription());
        return new ResponseEntity<>(factureService.create(facture), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Facture>> getAll() {
        return ResponseEntity.ok(factureService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Facture> getById(@PathVariable("id") Long id) {
        return factureService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Facture> update(@PathVariable Long id, @RequestBody Facture facture) {
        return ResponseEntity.ok(factureService.update(id, facture));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        factureService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/payer")
    public ResponseEntity<Facture> marquerPayee(@PathVariable("id") Long id) {
        return ResponseEntity.ok(factureService.marquerPayee(id));
    }

    @PostMapping("/{id}/envoyer")
    public ResponseEntity<Map<String, String>> envoyerAuChef(@PathVariable Long id) {
        System.out.println("📧 Envoi de la facture " + id + " au chef d'équipe");

        Optional<Facture> factureOpt = factureService.getById(id);
        if (factureOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Facture facture = factureOpt.get();
        System.out.println("📄 Facture #" + facture.getId() + " envoyée avec succès");

        Map<String, String> response = new HashMap<>();
        response.put("success", "true");
        response.put("message", "Facture envoyée avec succès");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/technicien/{technicienId}")
    public ResponseEntity<List<Facture>> getByTechnicien(@PathVariable String technicienId) {
        return ResponseEntity.ok(factureService.getByTechnicien(technicienId));
    }

    @GetMapping("/chef/{chefId}")
    public ResponseEntity<List<Facture>> getByChef(@PathVariable Long chefId) {
        return ResponseEntity.ok(factureService.getByChef(chefId));
    }

    @GetMapping("/demande/{demandeId}")
    public ResponseEntity<List<Facture>> getByDemande(@PathVariable Long demandeId) {
        return ResponseEntity.ok(factureService.getByTechnicien(String.valueOf(demandeId)));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            // Construire l'événement
            Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);

            // Vérifier le type d'événement
            if ("checkout.session.completed".equals(event.getType())) {
                // Récupérer la session
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElse(null);

                if (session != null) {
                    String factureIdStr = session.getClientReferenceId();
                    Long factureId = Long.valueOf(factureIdStr);
                    factureService.marquerPayee(factureId);
                    System.out.println("✅ Paiement reçu pour la facture #" + factureId);
                }
            }

            return ResponseEntity.ok("Webhook reçu avec succès");

        } catch (SignatureVerificationException e) {
            System.err.println("❌ Signature invalide: " + e.getMessage());
            return ResponseEntity.status(401).body("Signature invalide");
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            return ResponseEntity.status(500).body("Erreur interne");
        }
    }

    @PostMapping("/{id}/create-payment")
    public ResponseEntity<?> createPayment(@PathVariable("id") Long id) {
        try {
            Facture facture = factureService.getById(id)
                    .orElseThrow(() -> new RuntimeException("Facture non trouvée"));

            String paymentUrl = stripeService.createCheckoutSession(
                    facture.getId(),
                    facture.getCoutTotal(),
                    facture.getDescription()
            );

            Map<String, String> response = new HashMap<>();
            response.put("paymentUrl", paymentUrl);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("❌ Erreur création paiement: " + e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }


    @PostMapping("/{id}/create-payment-intent")
    public ResponseEntity<?> createPaymentIntent(@PathVariable("id") Long id) {
        try {
            System.out.println("🔵 Création payment intent pour facture: " + id);

            Facture facture = factureService.getById(id)
                    .orElseThrow(() -> new RuntimeException("Facture non trouvée"));

            System.out.println("💰 Montant: " + facture.getCoutTotal());

            // Initialiser Stripe avec la clé secrète
Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY");

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount((long)(facture.getCoutTotal() * 100))
                    .setCurrency("eur")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            System.out.println("✅ Client secret: " + intent.getClientSecret());

            return ResponseEntity.ok(Map.of("clientSecret", intent.getClientSecret()));
        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}