package tn.cityvoice.personnelservice.service;

import tn.cityvoice.personnelservice.entity.Equipe;
import tn.cityvoice.personnelservice.entity.Etat;

import java.util.List;
import java.util.UUID;

public interface IEquipe {
    Equipe getEquipeByNom(String nom);
    List<Equipe> getAllEquipes();
    Equipe getEquipeById(UUID id);
    List<Equipe> getEquipesBySpecialite(String specialite);
    Equipe addEquipe(Equipe equipe);
    void  updateEquipe(UUID id,Equipe equipe);
    void deleteEquipe(UUID id);
    void updateEquipeStatus(UUID id, Etat Etat);


}
