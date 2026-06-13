package tn.cityvoice.projetservice.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class StripeService {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.fail-url}")
    private String failUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    /**
     * Create a Stripe Checkout session
     * @param montant in DT (converted to millimes for Stripe)
     * @param paiementId our internal paiement ID
     * @param collecteId our collecte ID
     * @param description project title
     * @return Map with sessionId and checkoutUrl
     */
    public Map<String, String> createCheckoutSession(
            Float montant,
            Long paiementId,
            Long collecteId,
            String description
    ) {
        try {
            // Stripe uses smallest currency unit
            // TND = millimes (1 DT = 1000 millimes)
            long montantMillimes = Math.round(montant * 100);

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl
                            + "?session_id={CHECKOUT_SESSION_ID}"
                            + "&paiement=" + paiementId)
                    .setCancelUrl(failUrl)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("usd")
                                                    .setUnitAmount(montantMillimes)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData
                                                                    .ProductData.builder()
                                                                    .setName("Don — " + description)
                                                                    .setDescription(
                                                                            "Contribution au projet CityVoice"
                                                                    )
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);

            return Map.of(
                    "sessionId",   session.getId(),
                    "checkoutUrl", session.getUrl()
            );

        } catch (StripeException e) {
            log.error("Stripe session creation error: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Verify a Stripe session after redirect
     */

    public boolean verifySession(String sessionId) {
        try {
            log.info("Retrieving Stripe session: {}", sessionId);
            Session session = Session.retrieve(sessionId);
            log.info("Session status: {} paymentStatus: {}",
                    session.getStatus(), session.getPaymentStatus());
            return "paid".equals(session.getPaymentStatus())
                    || "complete".equals(session.getStatus());
        } catch (Exception e) {
            log.error("Stripe session retrieve failed: {}", e.getMessage(), e);
            return false;
        }
    }
}