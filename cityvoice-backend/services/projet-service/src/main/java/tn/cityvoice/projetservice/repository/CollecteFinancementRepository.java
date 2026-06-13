package tn.cityvoice.projetservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.projetservice.entity.CollecteFinancement;
import java.util.Optional;

public interface CollecteFinancementRepository
        extends JpaRepository<CollecteFinancement, Long> {
    Optional<CollecteFinancement> findByProjetId(Long projetId);
}