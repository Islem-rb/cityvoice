package tn.cityvoice.ressourceservice.services;

import org.springframework.stereotype.Service;
import tn.cityvoice.ressourceservice.entity.MaintenanceLog;
import tn.cityvoice.ressourceservice.repository.MaintenanceLogRepository;
import tn.cityvoice.ressourceservice.services.MaintenanceLogService;
import tn.cityvoice.ressourceservice.repository.MaintenanceLogRepository;


import java.util.List;
import java.util.Optional;

@Service
public class MaintenanceLogServiceImpl implements MaintenanceLogService {

    private final MaintenanceLogRepository repository;

    public MaintenanceLogServiceImpl(MaintenanceLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MaintenanceLog> getAll() {
        return repository.findAll();
    }

    @Override
    public Optional<MaintenanceLog> getById(Long id) {
        return repository.findById(id);
    }

    @Override
    public MaintenanceLog create(MaintenanceLog log) {
        System.out.println("📝 Création maintenance:");
        System.out.println("  - Type: " + log.getTypeIntervention());
        System.out.println("  - Description: " + log.getDescription());
        System.out.println("  - Date: " + log.getDate());
        System.out.println("  - RessourceId: " + log.getRessourceId());

        // Sauvegarde directe - JPA va utiliser le champ ressourceId
        MaintenanceLog saved = repository.save(log);
        System.out.println("✅ Maintenance sauvegardée avec ID: " + saved.getId());
        System.out.println("✅ RessourceId sauvegardé: " + saved.getRessourceId());

        return saved;
    }

    @Override
    public MaintenanceLog update(Long id, MaintenanceLog log) {
        MaintenanceLog existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Maintenance non trouvée"));

        existing.setTypeIntervention(log.getTypeIntervention());
        existing.setDescription(log.getDescription());
        existing.setDate(log.getDate());
        existing.setRessourceId(log.getRessourceId());

        return repository.save(existing);
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }


    @Override
    public List<MaintenanceLog> getByResourceId(Long resourceId) {
        return repository.findByRessourceId(resourceId);
    }



    @Override
    public int countByRessourceId(Long ressourceId) {
        return repository.countByRessourceId(ressourceId);
    }

    @Override
    public List<MaintenanceLog> findByTechnicienId(String technicienId) {
        MaintenanceLogServiceImpl maintenanceLogRepository = null;
        return maintenanceLogRepository.findByTechnicienId(technicienId);
    }


}
