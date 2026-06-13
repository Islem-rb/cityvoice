package tn.cityvoice.ressourceservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.ressourceservice.entity.MaintenanceLog;

import java.util.List;

public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, Long> {
    List<MaintenanceLog> findByRessourceId(Long ressourceId);
    int countByRessourceId(Long ressourceId);
    List<MaintenanceLog> findByTechnicienId(String technicienId);
}