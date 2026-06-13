package tn.cityvoice.ressourceservice.services;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import tn.cityvoice.ressourceservice.entity.Ressource;
import tn.cityvoice.ressourceservice.repository.RessourceRepository;
import tn.cityvoice.ressourceservice.services.RessourceService;

import java.util.List;
import java.util.Optional;

@Service
public class RessourceServiceImpl implements RessourceService {

    private final RessourceRepository repository;

    public RessourceServiceImpl(RessourceRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Ressource> getAll() {
        return repository.findAll();
    }

    @Override
    public Optional<Ressource> getById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Ressource create(Ressource ressource) {
        return repository.save(ressource);
    }

    @Override
    public Ressource update(Long id, Ressource ressource) {
        ressource.setId(id);
        return repository.save(ressource);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
