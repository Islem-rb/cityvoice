package tn.cityvoice.ressourceservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.ressourceservice.entity.DemandeMaintenance;
import tn.cityvoice.ressourceservice.services.DemandeMaintenanceService;

import java.util.List;

@RestController
@RequestMapping("/api/demandes-maintenance")
@RequiredArgsConstructor
public class DemandeMaintenanceController {

    private final DemandeMaintenanceService service;

    // ========== EXISTANTS ==========

    @PostMapping
    public ResponseEntity<DemandeMaintenance> create(@RequestBody DemandeMaintenance demande) {
        System.out.println("=========================================");
        System.out.println("📥 BACKEND - Demande reçue");
        System.out.println("📥 BACKEND - Matricule: " + demande.getRessourceMatricule());
        System.out.println("📥 BACKEND - Demande complète: " + demande);
        System.out.println("=========================================");

        if (demande.getRessourceMatricule() == null || demande.getRessourceMatricule().isEmpty()) {
            System.err.println("❌ BACKEND - Matricule NULL ou VIDE !");
            return ResponseEntity.badRequest().build();
        }

        DemandeMaintenance saved = service.create(demande);
        System.out.println("✅ BACKEND - Sauvegardé avec matricule: " + saved.getRessourceMatricule());
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<DemandeMaintenance>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/chef/{chefId}")
    public ResponseEntity<List<DemandeMaintenance>> getByChef(@PathVariable("chefId") String chefId) {
        return ResponseEntity.ok(service.getByChef(chefId));
    }

    @GetMapping("/en-attente")
    public ResponseEntity<List<DemandeMaintenance>> getEnAttente() {
        return ResponseEntity.ok(service.getEnAttente());
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<DemandeMaintenance> updateStatut(
            @PathVariable("id") Long id,
            @RequestParam("statut") String statut,
            @RequestParam(value = "maintenanceId", required = false) Long maintenanceId) {

        System.out.println("📝 Mise à jour statut - ID: " + id);
        System.out.println("📝 Statut: " + statut);
        System.out.println("📝 MaintenanceId: " + maintenanceId);

        DemandeMaintenance updated = service.updateStatut(id, statut, maintenanceId);
        return ResponseEntity.ok(updated);
    }

    // ========== NOUVEAUX ENDPOINTS ==========

    /**
     * 🔍 Récupérer une demande par son ID (pour modification)
     */
    @GetMapping("/{id}")
    public ResponseEntity<DemandeMaintenance> getById(@PathVariable Long id) {
        System.out.println("🔍 Récupération demande ID: " + id);
        DemandeMaintenance demande = service.getById(id);
        return ResponseEntity.ok(demande);
    }

    /**
     * ✏️ MODIFIER une demande de maintenance (chef d'équipe)
     */
    @PutMapping("/{id}")
    public ResponseEntity<DemandeMaintenance> update(
            @PathVariable("id") Long id,
            @RequestBody DemandeMaintenance demandeModifiee) {

        System.out.println("=========================================");
        System.out.println("✏️ MODIFICATION demande - ID: " + id);
        System.out.println("✏️ Nouveau motif: " + demandeModifiee.getMotif());
        System.out.println("✏️ Nouvelle urgence: " + demandeModifiee.getUrgence());
        System.out.println("✏️ Nouvelle date remise: " + demandeModifiee.getDateRemiseSouhaitee());
        System.out.println("=========================================");

        // Vérifier que la demande existe et est modifiable
        DemandeMaintenance updated = service.update(id, demandeModifiee);

        System.out.println("✅ Demande modifiée avec succès - ID: " + updated.getId());
        return ResponseEntity.ok(updated);
    }

    /**
     * 🗑️ SUPPRIMER une demande de maintenance (chef d'équipe)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        System.out.println("=========================================");
        System.out.println("🗑️ SUPPRESSION demande - ID: " + id);
        System.out.println("=========================================");

        service.delete(id);

        System.out.println("✅ Demande supprimée avec succès - ID: " + id);
        return ResponseEntity.noContent().build();
    }


    @PutMapping("/{id}/technicien")
    public ResponseEntity<DemandeMaintenance> assignerTechnicien(
            @PathVariable("id") Long id,
            @RequestParam String technicienId) {

        DemandeMaintenance demande = service.getById(id);
        demande.setTechnicienId(technicienId);
        demande.setStatut("EN_COURS");
        DemandeMaintenance updated = service.update(id, demande);
        return ResponseEntity.ok(updated);
    }




    @GetMapping("/technicien/{technicienId}")
    public ResponseEntity<List<DemandeMaintenance>> getDemandesByTechnicien(@PathVariable String technicienId) {
        List<DemandeMaintenance> demandes = service.findByTechnicienIdAndStatut(technicienId, "EN_COURS");
        return ResponseEntity.ok(demandes);
    }
}

// Classe pour les requêtes de statut (existante)
class StatutRequest {
    private String statut;
    private Long maintenanceId;

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Long getMaintenanceId() { return maintenanceId; }
    public void setMaintenanceId(Long maintenanceId) { this.maintenanceId = maintenanceId; }
}