package tn.cityvoice.ressourceservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.cityvoice.ressourceservice.entity.DemandeMaintenance;
import tn.cityvoice.ressourceservice.repository.DemandeMaintenanceRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DemandeMaintenanceServiceImpl implements DemandeMaintenanceService {

    private final DemandeMaintenanceRepository repository;

    @Override
    public DemandeMaintenance create(DemandeMaintenance demande) {
        demande.setDateDemande(LocalDateTime.now());
        demande.setStatut("EN_ATTENTE");
        return repository.save(demande);
    }

    @Override
    public List<DemandeMaintenance> getAll() {
        return repository.findAll();
    }

    @Override
    public List<DemandeMaintenance> getByChef(String chefId) {
        return repository.findByChefId(chefId);
    }

    @Override
    public List<DemandeMaintenance> getEnAttente() {
        return repository.findByStatut("EN_ATTENTE");
    }

    @Override
    public DemandeMaintenance updateStatut(Long id, String statut, Long maintenanceId) {
        System.out.println("🔧 Service updateStatut - ID: " + id + ", statut: " + statut + ", maintenanceId: " + maintenanceId);

        DemandeMaintenance demande = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée avec id: " + id));

        demande.setStatut(statut);
        if (maintenanceId != null) {
            demande.setMaintenanceId(maintenanceId);
        }

        return repository.save(demande);
    }



    // ========== NOUVEAUX ==========

    @Override
    public DemandeMaintenance getById(Long id) {
        System.out.println("🔍 Service getById - ID: " + id);
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande non trouvée avec id: " + id));
    }

    @Override
    public DemandeMaintenance update(Long id, DemandeMaintenance demandeModifiee) {
        System.out.println("✏️ Service update - ID: " + id);

        // 1. Récupérer la demande existante
        DemandeMaintenance demandeExistante = getById(id);

        // 2. Vérifier que la demande est modifiable (statut EN_ATTENTE uniquement)
        if (!"EN_ATTENTE".equals(demandeExistante.getStatut())) {
            throw new RuntimeException("Impossible de modifier une demande qui n'est plus en attente. Statut actuel: " + demandeExistante.getStatut());
        }

        // 3. Mettre à jour uniquement les champs autorisés
        demandeExistante.setMotif(demandeModifiee.getMotif());
        demandeExistante.setUrgence(demandeModifiee.getUrgence());
        demandeExistante.setDateRemiseSouhaitee(demandeModifiee.getDateRemiseSouhaitee());

        // ⚠️ Ne pas modifier ces champs :
        // - ressourceMatricule (conserve le matricule original)
        // - ressourceId (conserve l'ID original)
        // - chefId (conserve le chef original)
        // - dateDemande (conserve la date originale)
        // - statut (reste EN_ATTENTE si non traité)

        // 4. Sauvegarder
        DemandeMaintenance saved = repository.save(demandeExistante);
        System.out.println("✅ Service - Demande modifiée avec succès - ID: " + saved.getId());

        return saved;
    }

    @Override
    public void delete(Long id) {
        System.out.println("🗑️ Service delete - ID: " + id);

        // 1. Vérifier que la demande existe
        DemandeMaintenance demandeExistante = getById(id);

        // 2. Vérifier que la demande peut être supprimée (statut EN_ATTENTE uniquement)
        if (!"EN_ATTENTE".equals(demandeExistante.getStatut())) {
            throw new RuntimeException("Impossible de supprimer une demande qui n'est plus en attente. Statut actuel: " + demandeExistante.getStatut());
        }

        // 3. Supprimer
        repository.delete(demandeExistante);
        System.out.println("✅ Service - Demande supprimée avec succès - ID: " + id);
    }



    @Override
    public List<DemandeMaintenance> findByTechnicienIdAndStatut(String technicienId, String statut) {
        return repository.findByTechnicienIdAndStatut(technicienId, statut);
    }
}