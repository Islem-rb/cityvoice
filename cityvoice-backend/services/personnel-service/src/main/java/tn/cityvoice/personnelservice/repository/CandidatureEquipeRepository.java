package tn.cityvoice.personnelservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.personnelservice.entity.CandidatureEquipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidatureEquipeRepository extends JpaRepository<CandidatureEquipe, UUID> {
    CandidatureEquipe findByStatut(String statut);
    Optional<CandidatureEquipe> findByEquipeIdAndFonction(UUID equipeId, String fonction);
    List<CandidatureEquipe> findByEquipeId(UUID equipeId);

    /* Optional<CandidatureEquipe> findByStatut(String statut);*/

}
