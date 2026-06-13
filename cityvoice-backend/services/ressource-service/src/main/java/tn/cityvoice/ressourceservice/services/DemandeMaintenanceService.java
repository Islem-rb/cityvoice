package tn.cityvoice.ressourceservice.services;

import tn.cityvoice.ressourceservice.entity.DemandeMaintenance;
import java.util.List;

public interface DemandeMaintenanceService {

    // EXISTANTS
    DemandeMaintenance create(DemandeMaintenance demande);
    List<DemandeMaintenance> getAll();
    List<DemandeMaintenance> getByChef(String chefId);
    List<DemandeMaintenance> getEnAttente();
    DemandeMaintenance updateStatut(Long id, String statut, Long maintenanceId);

    // 🔥 NOUVEAUX - Pour modification et suppression
    DemandeMaintenance getById(Long id);
    DemandeMaintenance update(Long id, DemandeMaintenance demandeModifiee);
    void delete(Long id);

    List<DemandeMaintenance> findByTechnicienIdAndStatut(String technicienId, String enCours);
}