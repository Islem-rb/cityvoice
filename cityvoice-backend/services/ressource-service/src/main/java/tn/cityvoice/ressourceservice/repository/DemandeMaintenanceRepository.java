package tn.cityvoice.ressourceservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.ressourceservice.entity.DemandeMaintenance;
import java.util.List;

public interface DemandeMaintenanceRepository extends JpaRepository<DemandeMaintenance, Long> {
    List<DemandeMaintenance> findByChefId(String chefId);
    List<DemandeMaintenance> findByStatut(String statut);
    List<DemandeMaintenance> findByRessourceId(Long ressourceId);
    List<DemandeMaintenance> findByTechnicienIdAndStatut(String technicienId, String statut);
    List<DemandeMaintenance> findByTechnicienId(String technicienId);

}