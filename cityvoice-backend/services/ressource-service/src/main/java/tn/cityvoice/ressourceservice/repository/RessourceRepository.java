package tn.cityvoice.ressourceservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.ressourceservice.entity.Ressource;

public interface RessourceRepository extends JpaRepository<Ressource, Long> {
}
