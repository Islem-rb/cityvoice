package tn.cityvoice.ressourceservice.services;

import tn.cityvoice.ressourceservice.entity.MaintenanceLog;

import java.util.List;
import java.util.Optional;

public interface MaintenanceLogService {
    List<MaintenanceLog> getAll();
    Optional<MaintenanceLog> getById(Long id);
    MaintenanceLog create(MaintenanceLog log);
    MaintenanceLog update(Long id, MaintenanceLog log);
    void delete(Long id);
    List<MaintenanceLog> getByResourceId(Long resourceId);
    int countByRessourceId(Long ressourceId);
    List<MaintenanceLog> findByTechnicienId(String technicienId);
}

