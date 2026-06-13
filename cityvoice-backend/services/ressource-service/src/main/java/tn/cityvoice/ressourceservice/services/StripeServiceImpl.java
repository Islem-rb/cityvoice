package tn.cityvoice.ressourceservice.services.impl;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.cityvoice.ressourceservice.services.StripeService;

@Service
public class StripeServiceImpl implements StripeService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public String createCheckoutSession(Long factureId, Double montant, String description) {
        // Initialiser Stripe avec la clé secrète
        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/payment/success?factureId=" + factureId)
                .setCancelUrl(frontendUrl + "/payment/cancel")
                .setClientReferenceId(String.valueOf(factureId))
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("eur")
                                                .setUnitAmount((long)(montant * 100))
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Maintenance")
                                                                .setDescription(description)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        try {
            Session session = Session.create(params);
            return session.getUrl();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erreur création session Stripe: " + e.getMessage());
        }
    }
}