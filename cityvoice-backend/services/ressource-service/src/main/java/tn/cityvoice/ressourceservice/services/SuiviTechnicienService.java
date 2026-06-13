package tn.cityvoice.ressourceservice.services;

import tn.cityvoice.ressourceservice.entity.SuiviTechnicien;
import java.util.List;
import java.util.Optional;

public interface SuiviTechnicienService {

    // Démarrer une intervention
    SuiviTechnicien demarrerIntervention(Long maintenanceId, byte[] technicienId);

    // Changer de statut (DEJEUNER, PAUSE_WC, REUNION, EN_SERVICE)
    SuiviTechnicien changerStatut(Long suiviId, String nouveauStatut);

    // Mettre hors ligne (fin de journée, reprise possible demain)
    SuiviTechnicien mettreHorsLigne(Long suiviId);

    // Terminer l'intervention
    SuiviTechnicien terminerIntervention(Long suiviId);

    // Récupérer le suivi en cours d'une maintenance
    Optional<SuiviTechnicien> getSuiviEnCours(Long maintenanceId);

    // Récupérer l'historique d'un technicien
    List<SuiviTechnicien> getHistoriqueTechnicien(byte[] technicienId);

    // Calculer le temps total travaillé pour une maintenance
    int calculerTempsTotal(Long maintenanceId);
}