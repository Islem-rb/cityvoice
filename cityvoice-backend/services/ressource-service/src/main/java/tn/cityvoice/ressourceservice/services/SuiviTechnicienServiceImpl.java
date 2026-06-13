package tn.cityvoice.ressourceservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.ressourceservice.entity.SuiviTechnicien;
import tn.cityvoice.ressourceservice.repository.SuiviTechnicienRepository;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SuiviTechnicienServiceImpl implements SuiviTechnicienService {

    private final SuiviTechnicienRepository suiviTechnicienRepository;

    @Override
    public SuiviTechnicien demarrerIntervention(Long maintenanceId, byte[] technicienId) {
        // Vérifier s'il n'y a pas déjà un suivi en cours
        Optional<SuiviTechnicien> existing = suiviTechnicienRepository.findByMaintenanceIdAndFinIsNull(maintenanceId);
        if (existing.isPresent()) {
            throw new RuntimeException("Une intervention est déjà en cours pour cette maintenance");
        }

        SuiviTechnicien suivi = new SuiviTechnicien();
        suivi.setTechnicienId(technicienId);
        suivi.setMaintenanceId(maintenanceId);
        suivi.setStatut("EN_SERVICE");
        suivi.setDebut(LocalDateTime.now());
        suivi.setEstPaye(true);

        return suiviTechnicienRepository.save(suivi);
    }

    @Override
    public SuiviTechnicien changerStatut(Long suiviId, String nouveauStatut) {
        SuiviTechnicien suivi = suiviTechnicienRepository.findById(suiviId)
                .orElseThrow(() -> new RuntimeException("Suivi non trouvé"));

        // Enregistrer la fin du segment actuel
        LocalDateTime maintenant = LocalDateTime.now();
        suivi.setFin(maintenant);

        // Calculer la durée de ce segment
        Duration duration = Duration.between(suivi.getDebut(), maintenant);
        suivi.setDureeSecondes((int) duration.getSeconds());

        // Sauvegarder le segment terminé
        suiviTechnicienRepository.save(suivi);

        // Créer un nouveau segment avec le nouveau statut
        SuiviTechnicien nouveauSuivi = new SuiviTechnicien();
        nouveauSuivi.setTechnicienId(suivi.getTechnicienId());
        nouveauSuivi.setMaintenanceId(suivi.getMaintenanceId());
        nouveauSuivi.setStatut(nouveauStatut);
        nouveauSuivi.setDebut(maintenant);

        // Déterminer si ce statut est payé
        boolean estPaye = nouveauStatut.equals("EN_SERVICE") ||
                nouveauStatut.equals("PAUSE_WC") ||
                nouveauStatut.equals("REUNION");
        nouveauSuivi.setEstPaye(estPaye);

        return suiviTechnicienRepository.save(nouveauSuivi);
    }

    @Override
    public SuiviTechnicien mettreHorsLigne(Long suiviId) {
        return changerStatut(suiviId, "HORS_LIGNE");
    }

    @Override
    public SuiviTechnicien terminerIntervention(Long suiviId) {
        return changerStatut(suiviId, "TERMINE");
    }

    @Override
    public Optional<SuiviTechnicien> getSuiviEnCours(Long maintenanceId) {
        return suiviTechnicienRepository.findByMaintenanceIdAndFinIsNull(maintenanceId);
    }

    @Override
    public List<SuiviTechnicien> getHistoriqueTechnicien(byte[] technicienId) {
        return suiviTechnicienRepository.findByTechnicienIdOrderByDebutDesc(technicienId);
    }

    @Override
    public int calculerTempsTotal(Long maintenanceId) {
        List<SuiviTechnicien> suivis = suiviTechnicienRepository.findByMaintenanceIdOrderByDebutAsc(maintenanceId);

        int totalSecondes = 0;
        for (SuiviTechnicien suivi : suivis) {
            if (suivi.getEstPaye() && suivi.getDureeSecondes() != null) {
                totalSecondes += suivi.getDureeSecondes();
            }
        }
        return totalSecondes;
    }
}