package tn.cityvoice.personnelservice.service;

import tn.cityvoice.personnelservice.entity.Fonction;
import tn.cityvoice.personnelservice.entity.MembreEquipe;

import java.util.List;
import java.util.UUID;

public interface IMembreEquipe {
    MembreEquipe getMembreEquipe(UUID id);
    List<MembreEquipe> getAllMembreEquipe();
    void addMembreEquipe(MembreEquipe membreEquipe);
    void deleteMembreEquipe(UUID id);
    void updateMembreEquipe(UUID id,MembreEquipe membreEquipe);
    List<MembreEquipe> getMembreEquipe(Fonction fonction);
    List<MembreEquipe> getAllMembreEquipeByNom(String nom);
    void updateFonction(UUID id,Fonction fonction);
}
