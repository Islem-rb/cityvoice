package tn.cityvoice.ressourceservice.repository;

import tn.cityvoice.ressourceservice.entity.SuiviTechnicien;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SuiviTechnicienRepository extends JpaRepository<SuiviTechnicien, Long> {

    // Récupérer le suivi actif d'une maintenance (non terminé)
    Optional<SuiviTechnicien> findByMaintenanceIdAndStatutNot(Long maintenanceId, String statut);

    // Récupérer tous les suivis d'un technicien
    List<SuiviTechnicien> findByTechnicienIdOrderByDebutDesc(byte[] technicienId);

    // Récupérer le dernier suivi d'un technicien
    @Query("SELECT s FROM SuiviTechnicien s WHERE s.technicienId = ?1 ORDER BY s.debut DESC LIMIT 1")
    Optional<SuiviTechnicien> findLastByTechnicienId(byte[] technicienId);

    // Récupérer le suivi en cours d'une maintenance
    Optional<SuiviTechnicien> findByMaintenanceIdAndFinIsNull(Long maintenanceId);

    List<SuiviTechnicien> findByMaintenanceIdOrderByDebutAsc(Long maintenanceId);
}