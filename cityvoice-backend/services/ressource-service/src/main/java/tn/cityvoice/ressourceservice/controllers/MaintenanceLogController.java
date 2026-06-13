package tn.cityvoice.ressourceservice.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.ressourceservice.entity.DemandeMaintenance;
import tn.cityvoice.ressourceservice.entity.MaintenanceLog;
import tn.cityvoice.ressourceservice.services.DemandeMaintenanceService;
import tn.cityvoice.ressourceservice.services.MaintenanceLogService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/maintenance-logs")
public class MaintenanceLogController {

    private final MaintenanceLogService service;
    private final DemandeMaintenanceService demandeMaintenanceService;  // ← AJOUTER CETTE LIGNE

    public MaintenanceLogController(MaintenanceLogService service, DemandeMaintenanceService demandeMaintenanceService) {
        this.service = service;
        this.demandeMaintenanceService = demandeMaintenanceService;
    }

    // GET all
    @GetMapping
    public ResponseEntity<List<MaintenanceLog>> getAll() {
        return new ResponseEntity<>(service.getAll(), HttpStatus.OK);
    }

    // GET by ID
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceLog> getById(@PathVariable("id") Long id) {
        return service.getById(id)
                .map(log -> new ResponseEntity<>(log, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // POST create
    @PostMapping
    public ResponseEntity<MaintenanceLog> create(@RequestBody MaintenanceLog log) {
        System.out.println("📥 POST reçu - log complet: " + log);
        System.out.println("  - Type: " + log.getTypeIntervention());
        System.out.println("  - Description: " + log.getDescription());
        System.out.println("  - Date: " + log.getDate());
        System.out.println("  - RessourceId: " + log.getRessourceId());
        System.out.println("  - recurrence: " + log.getRecurrence());
        System.out.println("  - uniteRecurrence: " + log.getUniteRecurrence());
        System.out.println("  - prochaineMaintenance: " + log.getProchaineMaintenance());

        MaintenanceLog saved = service.create(log);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    // PUT update
    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceLog> update(@PathVariable("id") Long id, @RequestBody MaintenanceLog log) {



        System.out.println("📥 Mise à jour maintenance ID: " + id);
        System.out.println("  - recurrence: " + log.getRecurrence());
        System.out.println("  - uniteRecurrence: " + log.getUniteRecurrence());
        System.out.println("  - prochaineMaintenance: " + log.getProchaineMaintenance());
        return new ResponseEntity<>(service.update(id, log), HttpStatus.OK);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }


    @GetMapping("/resource/{resourceId}")
    public ResponseEntity<List<MaintenanceLog>> getByResourceId(@PathVariable("resourceId") Long resourceId) {
        return new ResponseEntity<>(service.getByResourceId(resourceId), HttpStatus.OK);
    }





    @GetMapping("/technicien/{technicienId}")
    public ResponseEntity<List<MaintenanceLog>> getMaintenancesByTechnicien(@PathVariable String technicienId) {
        List<MaintenanceLog> maintenances = service.findByTechnicienId(technicienId);
        return ResponseEntity.ok(maintenances);
    }



    @PostMapping("/terminer")
    public ResponseEntity<?> terminerIntervention(@RequestBody Map<String, Object> rapport) {
        try {
            Long interventionId = Long.valueOf(rapport.get("interventionId").toString());

            // Récupérer et mettre à jour la demande
            DemandeMaintenance demande = demandeMaintenanceService.getById(interventionId);
            demande.setStatut("TERMINEE");
            demandeMaintenanceService.update(demande.getId(), demande);

            // Créer le log de maintenance
            MaintenanceLog maintenanceLog = new MaintenanceLog();
            maintenanceLog.setRessourceId(interventionId);
            maintenanceLog.setDescription(rapport.get("details").toString());
            maintenanceLog.setDate(LocalDateTime.now());
            maintenanceLog.setCout(Double.valueOf(rapport.get("montant").toString()));
            maintenanceLog.setTechnicienId(rapport.get("technicienId").toString());

            service.create(maintenanceLog);

            System.out.println("✅ Intervention terminée avec succès");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Intervention terminée avec succès"
            ));

        } catch (Exception e) {
            System.err.println("❌ Erreur: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Erreur: " + e.getMessage()
            ));
        }
    }


}
