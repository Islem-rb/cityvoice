package tn.cityvoice.projetservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.projetservice.entity.VoteProjet;
import java.util.List;

public interface VoteProjetRepository extends JpaRepository<VoteProjet, Long> {
    List<VoteProjet> findByProjetId(Long projetId);
    List<VoteProjet> findByUserId(String userId);
    long countByProjetIdAndValeur(Long projetId, Boolean valeur);
}