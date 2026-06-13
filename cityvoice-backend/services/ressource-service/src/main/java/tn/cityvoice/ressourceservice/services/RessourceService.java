package tn.cityvoice.ressourceservice.services;

import tn.cityvoice.ressourceservice.entity.Ressource;

import java.util.List;
import java.util.Optional;

public interface RessourceService {
    List<Ressource> getAll();
    Optional<Ressource> getById(Long id);
    Ressource create(Ressource ressource);
    Ressource update(Long id, Ressource ressource);
    void delete(Long id);

}