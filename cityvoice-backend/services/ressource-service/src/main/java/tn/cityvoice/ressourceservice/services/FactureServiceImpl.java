// tn/cityvoice/ressourceservice/services/impl/FactureServiceImpl.java
package tn.cityvoice.ressourceservice.services.impl;

import tn.cityvoice.ressourceservice.entity.Facture;
import tn.cityvoice.ressourceservice.repository.FactureRepository;
import tn.cityvoice.ressourceservice.services.FactureService;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FactureServiceImpl implements FactureService {

    private final FactureRepository factureRepository;

    public FactureServiceImpl(FactureRepository factureRepository) {
        this.factureRepository = factureRepository;
    }

    @Override
    public Facture create(Facture facture) {
        facture.setDateEmission(LocalDateTime.now());
        facture.setStatut("EN_ATTENTE");
        return factureRepository.save(facture);
    }

    @Override
    public List<Facture> getAll() {
        return factureRepository.findAll();
    }

    @Override
    public Optional<Facture> getById(Long id) {
        return factureRepository.findById(id);
    }

    @Override
    public Facture update(Long id, Facture facture) {
        facture.setId(id);
        return factureRepository.save(facture);
    }

    @Override
    public void delete(Long id) {
        factureRepository.deleteById(id);
    }

    @Override
    public Facture marquerPayee(Long id) {
        Facture facture = factureRepository.findById(id).orElseThrow();
        facture.setStatut("PAYEE");
        facture.setDatePaiement(LocalDateTime.now());
        return factureRepository.save(facture);
    }

    @Override
    public List<Facture> getByTechnicien(String technicienId) {
        return factureRepository.findByTechnicienId(technicienId);
    }

    @Override
    public List<Facture> getByChef(Long chefId) {
        return factureRepository.findByChefId(chefId);
    }

    @Override
    public List<Facture> getByDemande(Long demandeId) {
        return factureRepository.findByDemandeId(demandeId);
    }
}