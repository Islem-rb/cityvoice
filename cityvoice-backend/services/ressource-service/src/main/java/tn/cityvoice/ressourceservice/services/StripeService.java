// StripeService.java (interface)
package tn.cityvoice.ressourceservice.services;

public interface StripeService {
    String createCheckoutSession(Long factureId, Double montant, String description);
}