package tn.cityvoice.projetservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.projetservice.entity.*;
import tn.cityvoice.projetservice.entity.enums.StatutCollecte;
import tn.cityvoice.projetservice.entity.enums.StatutPaiement;
import tn.cityvoice.projetservice.repository.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaiementServiceImpl implements PaiementService {

    private final PaiementRepository            paiementRepo;
    private final CollecteFinancementRepository collecteRepo;

    @Override
    @Transactional
    public Paiement pay(Long collecteId, Paiement paiement) {
        CollecteFinancement collecte = collecteRepo.findById(collecteId)
                .orElseThrow(() ->
                        new RuntimeException("Collecte introuvable"));

        if (collecte.getStatut() != StatutCollecte.ACTIVE) {
            throw new RuntimeException("Cette collecte n'est plus active");
        }

        paiement.setCollecte(collecte);
        if (paiement.getStatut() == null)
            paiement.setStatut(StatutPaiement.CONFIRME);
        paiementRepo.save(paiement);

        collecte.setMontantCollecte(
                collecte.getMontantCollecte() + paiement.getMontant()
        );
        if (collecte.getMontantCollecte() >= collecte.getMontantCible()) {
            collecte.setStatut(StatutCollecte.OBJECTIF_ATTEINT);
        }
        collecteRepo.save(collecte);

        return paiement;
    }

    @Override
    public List<Paiement> getByCollecte(Long collecteId) {
        return paiementRepo.findByCollecteId(collecteId);
    }

    @Override
    public List<Paiement> getByUser(String userId) {
        return paiementRepo.findByUserId(userId);
    }
}