package tn.cityvoice.projetservice.service;

import tn.cityvoice.projetservice.entity.Projet;
import tn.cityvoice.projetservice.entity.VoteProjet;
import tn.cityvoice.projetservice.entity.enums.StatutProjet;
import java.util.List;

public interface ProjetService {
    List<Projet> getAll();
    List<Projet> getByStatut(StatutProjet statut);
    List<Projet> getByCategorie(String categorie);
    Projet getById(Long id);
    Projet create(Projet projet);
    Projet update(Long id, Projet projet);
    Projet updateStatut(Long id, StatutProjet statut);
    Projet vote(Long projetId, VoteProjet vote);
    void delete(Long id);
}