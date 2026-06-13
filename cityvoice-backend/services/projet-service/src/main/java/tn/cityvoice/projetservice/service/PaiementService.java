package tn.cityvoice.projetservice.service;

import tn.cityvoice.projetservice.entity.Paiement;
import java.util.List;

public interface PaiementService {
    Paiement pay(Long collecteId, Paiement paiement);
    List<Paiement> getByCollecte(Long collecteId);
    List<Paiement> getByUser(String userId);
}