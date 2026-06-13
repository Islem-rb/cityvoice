package tn.cityvoice.ressourceservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.ressourceservice.entity.SuiviTechnicien;
import tn.cityvoice.ressourceservice.services.SuiviTechnicienService;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/suivi-technicien")
@RequiredArgsConstructor
public class SuiviTechnicienController {

    private final SuiviTechnicienService suiviTechnicienService;

    // Démarrer une intervention
    @PostMapping("/demarrer")
    public ResponseEntity<?> demarrerIntervention(
            @RequestParam Long maintenanceId,
            @RequestParam String technicienId) {

        try {
            byte[] technicienIdBytes = hexStringToByteArray(technicienId);
            SuiviTechnicien suivi = suiviTechnicienService.demarrerIntervention(maintenanceId, technicienIdBytes);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Intervention démarrée");
            response.put("suiviId", suivi.getId());
            response.put("statut", suivi.getStatut());
            response.put("debut", suivi.getDebut());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Changer de statut
    @PutMapping("/{suiviId}/statut")
    public ResponseEntity<?> changerStatut(
            @PathVariable Long suiviId,
            @RequestParam String nouveauStatut) {

        try {
            SuiviTechnicien suivi = suiviTechnicienService.changerStatut(suiviId, nouveauStatut);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Statut changé en " + nouveauStatut);
            response.put("statut", suivi.getStatut());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Mettre hors ligne
    @PutMapping("/{suiviId}/hors-ligne")
    public ResponseEntity<?> mettreHorsLigne(@PathVariable Long suiviId) {
        try {
            SuiviTechnicien suivi = suiviTechnicienService.mettreHorsLigne(suiviId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Mis hors ligne");
            response.put("tempsTotal", suiviTechnicienService.calculerTempsTotal(suivi.getMaintenanceId()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Terminer l'intervention
    @PutMapping("/{suiviId}/terminer")
    public ResponseEntity<?> terminerIntervention(@PathVariable Long suiviId) {
        try {
            SuiviTechnicien suivi = suiviTechnicienService.terminerIntervention(suiviId);

            int tempsTotal = suiviTechnicienService.calculerTempsTotal(suivi.getMaintenanceId());
            double montant = (tempsTotal / 3600.0) * 25; // 25€/heure

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Intervention terminée");
            response.put("tempsTotalSecondes", tempsTotal);
            response.put("montant", Math.round(montant));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Récupérer le suivi en cours d'une maintenance
    @GetMapping("/en-cours/{maintenanceId}")
    public ResponseEntity<?> getSuiviEnCours(@PathVariable Long maintenanceId) {
        var suivi = suiviTechnicienService.getSuiviEnCours(maintenanceId);
        if (suivi.isPresent()) {
            return ResponseEntity.ok(suivi.get());
        }
        return ResponseEntity.ok(Map.of("enCours", false));
    }

    // Calculer le temps total d'une maintenance
    @GetMapping("/temps-total/{maintenanceId}")
    public ResponseEntity<?> getTempsTotal(@PathVariable Long maintenanceId) {
        int secondes = suiviTechnicienService.calculerTempsTotal(maintenanceId);
        int heures = secondes / 3600;
        int minutes = (secondes % 3600) / 60;

        return ResponseEntity.ok(Map.of(
                "secondes", secondes,
                "heures", heures,
                "minutes", minutes,
                "montant", Math.round((secondes / 3600.0) * 25)
        ));
    }

    // Helper : convertir String hex en byte[]
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}