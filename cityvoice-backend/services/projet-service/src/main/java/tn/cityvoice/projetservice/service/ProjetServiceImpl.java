package tn.cityvoice.projetservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.projetservice.entity.*;
import tn.cityvoice.projetservice.entity.enums.StatutProjet;
import tn.cityvoice.projetservice.repository.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjetServiceImpl implements ProjetService {

    private final ProjetRepository              projetRepo;
    private final VoteProjetRepository          voteRepo;
    private final CollecteFinancementRepository collecteRepo;

    @Override
    public List<Projet> getAll() {
        return projetRepo.findAll();
    }

    @Override
    public List<Projet> getByStatut(StatutProjet statut) {
        return projetRepo.findByStatut(statut);
    }

    @Override
    public List<Projet> getByCategorie(String categorie) {
        return projetRepo.findByCategorie(categorie);
    }

    @Override
    public Projet getById(Long id) {
        return projetRepo.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Projet introuvable : " + id));
    }

    @Override
    @Transactional
    public Projet create(Projet projet) {
        // Extract collecte before saving projet
        CollecteFinancement collecte = projet.getCollecte();
        projet.setCollecte(null);
        projet.setVotes(null);

        Projet saved = projetRepo.save(projet);

        // Save collecte linked to saved projet
        if (collecte != null && collecte.getMontantCible() != null) {
            collecte.setProjet(saved);
            if (collecte.getMontantCollecte() == null)
                collecte.setMontantCollecte(0f);
            CollecteFinancement savedCollecte = collecteRepo.save(collecte);
            saved.setCollecte(savedCollecte);
        }

        return saved;
    }

    @Override
    @Transactional
    public Projet update(Long id, Projet updated) {
        Projet projet = getById(id);

        if (updated.getTitre()       != null) projet.setTitre(updated.getTitre());
        if (updated.getDescription() != null) projet.setDescription(updated.getDescription());
        if (updated.getCategorie()   != null) projet.setCategorie(updated.getCategorie());
        if (updated.getLocation()    != null) projet.setLocation(updated.getLocation());
        if (updated.getTags()        != null) projet.setTags(updated.getTags());
        if (updated.getImage()       != null) projet.setImage(updated.getImage());
        if (updated.getAdminNom()    != null) projet.setAdminNom(updated.getAdminNom());
        projet.setDureeDays(updated.getDureeDays());
        projet.setDateDebut(updated.getDateDebut());
        if (updated.getDateDebut() != null && updated.getDureeDays() > 0) {
            projet.setDateFin(updated.getDateDebut().plusDays(updated.getDureeDays()));
        }
        Projet saved = projetRepo.save(projet);
        if (updated.getCollecte() != null
                && updated.getCollecte().getMontantCible() != null) {
            CollecteFinancement collecte =
                    collecteRepo.findByProjetId(saved.getId()).orElse(null);
            if (collecte != null) {
                collecte.setMontantCible(updated.getCollecte().getMontantCible());
                collecteRepo.save(collecte);
            } else {
                CollecteFinancement newCollecte = CollecteFinancement.builder()
                        .projet(saved)
                        .montantCible(updated.getCollecte().getMontantCible())
                        .montantCollecte(0f)
                        .build();
                collecteRepo.save(newCollecte);
            }
        }
        return saved;
    }
    @Override
    @Transactional
    public Projet updateStatut(Long id, StatutProjet statut) {
        Projet projet = getById(id);
        projet.setStatut(statut);
        return projetRepo.save(projet);
    }

    @Override
    @Transactional
    public Projet vote(Long projetId, VoteProjet voteReq) {
        Projet projet = getById(projetId);
        voteReq.setProjet(projet);
        voteRepo.save(voteReq);

        if (Boolean.TRUE.equals(voteReq.getValeur())) {
            projet.setVotePour(projet.getVotePour() + 1);
        } else {
            projet.setVoteContre(projet.getVoteContre() + 1);
        }
        projet.setTotalVotes(projet.getTotalVotes() + 1);
        return projetRepo.save(projet);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        projetRepo.deleteById(id);
    }
}