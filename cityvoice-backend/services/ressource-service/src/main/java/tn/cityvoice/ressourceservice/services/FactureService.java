// tn/cityvoice/ressourceservice/services/FactureService.java
package tn.cityvoice.ressourceservice.services;

import tn.cityvoice.ressourceservice.entity.Facture;
import java.util.List;
import java.util.Optional;

public interface FactureService {
    Facture create(Facture facture);
    List<Facture> getAll();
    Optional<Facture> getById(Long id);
    Facture update(Long id, Facture facture);
    void delete(Long id);
    Facture marquerPayee(Long id);
    List<Facture> getByTechnicien(String technicienId);
    List<Facture> getByChef(Long chefId);
    List<Facture> getByDemande(Long demandeId);
}