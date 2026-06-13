package tn.cityvoice.evenementservice.controller;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.request.InscriptionRequest;
import tn.cityvoice.evenementservice.dto.response.ParticipantResponse;
import tn.cityvoice.evenementservice.entity.EvenementNotification;
import tn.cityvoice.evenementservice.entity.Participant;
import tn.cityvoice.evenementservice.enums.StatutPaiement;
import tn.cityvoice.evenementservice.service.EvenementNotificationService;
import tn.cityvoice.evenementservice.service.EvenementService;
import tn.cityvoice.evenementservice.repository.ParticipantRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class PaymentController {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    private final EvenementService evenementService;
    private final ParticipantRepository participantRepository;
    private final EvenementNotificationService notificationService;

    @PostMapping("/create-session")
    public ResponseEntity<Map<String, String>> createSession(
            @RequestParam Long evenementId,
            @RequestBody InscriptionRequest req) {

        try {
            Stripe.apiKey = stripeSecretKey;

            // 1. Inscrire le participant d'abord
            Participant participant = evenementService.inscrireParticipant(evenementId, req);

            // 2. Récupérer l'événement pour le prix et le titre
            var evenement = evenementService.findById(evenementId);
            long prixCentimes = evenement.getPrix()
                    .multiply(new java.math.BigDecimal("100"))
                    .longValue();

            // 3. Créer la session Stripe
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + "?participantId=" + participant.getId() + "&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl + "?participantId=" + participant.getId())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("eur")
                                                    .setUnitAmount(prixCentimes)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(evenement.getTitre())
                                                                    .setDescription("Inscription à l'événement : " + evenement.getTitre())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("participantId", String.valueOf(participant.getId()))
                    .putMetadata("evenementId", String.valueOf(evenementId))
                    .build();

            Session session = Session.create(params);

            // 4. Mettre à jour statut paiement → EN_ATTENTE
            participant.setStatutPaiement(StatutPaiement.EN_ATTENTE);
            participantRepository.save(participant);

            log.info("Session Stripe créée pour participant {}", participant.getId());
            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getId(),
                    "url", session.getUrl(),
                    "participantId", String.valueOf(participant.getId())
            ));

        } catch (Exception e) {
            log.error("Erreur Stripe : {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/confirm/{participantId}")
    public ResponseEntity<ParticipantResponse> confirmerPaiement(
            @PathVariable Long participantId) {
        try {
            Participant p = participantRepository.findById(participantId)
                    .orElseThrow(() -> new RuntimeException("Participant introuvable"));
            p.setStatutPaiement(StatutPaiement.PAYE);
            participantRepository.save(p);
            // ← Notifier le citoyen — paiement confirmé
            var evenement = p.getEvenement();
            notificationService.creer(
                    p.getCitoyenId(),
                    "Paiement confirmé 💳",
                    "Votre paiement pour \"" + evenement.getTitre() + "\" a été confirmé.",
                    EvenementNotification.TypeNotification.PAIEMENT,
                    evenement.getId(),
                    evenement.getTitre()
            );
            log.info("Paiement confirmé pour participant {}", participantId);
            return ResponseEntity.ok(ParticipantResponse.builder()
                    .id(p.getId())
                    .nomCitoyen(p.getNomCitoyen())
                    .emailCitoyen(p.getEmailCitoyen())
                    .qrToken(p.getQrToken())
                    .statutPresence(p.getStatutPresence().name())
                    .evenementId(p.getEvenement().getId())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/especes/{participantId}")
    public ResponseEntity<ParticipantResponse> reserverEspeces(
            @PathVariable Long participantId) {
        try {
            Participant p = participantRepository.findById(participantId)
                    .orElseThrow(() -> new RuntimeException("Participant introuvable"));
            p.setStatutPaiement(StatutPaiement.EN_ATTENTE_ESPECES);
            participantRepository.save(p);
            // ← Notifier le citoyen — réservation espèces
            var evenement = p.getEvenement();
            notificationService.creer(
                    p.getCitoyenId(),
                    "Réservation confirmée 🎫",
                    "Votre réservation pour \"" + evenement.getTitre()
                            + "\" est confirmée. Payez en espèces le jour J.",
                    EvenementNotification.TypeNotification.PAIEMENT,
                    evenement.getId(),
                    evenement.getTitre()
            );
            log.info("Réservation espèces pour participant {}", participantId);
            return ResponseEntity.ok(ParticipantResponse.builder()
                    .id(p.getId())
                    .nomCitoyen(p.getNomCitoyen())
                    .emailCitoyen(p.getEmailCitoyen())
                    .qrToken(p.getQrToken())
                    .statutPresence(p.getStatutPresence().name())
                    .evenementId(p.getEvenement().getId())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}