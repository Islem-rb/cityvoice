package tn.cityvoice.projetservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.projetservice.entity.Paiement;
import java.util.List;
import java.util.Optional;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    List<Paiement> findByCollecteId(Long collecteId);
    List<Paiement> findByUserId(String userId);
    List<Paiement> findByUserIdOrderByDateDesc(String userId);
    Optional<Paiement> findByReference(String reference);
}