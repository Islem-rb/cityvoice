package tn.cityvoice.personnelservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.personnelservice.entity.QuizzResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizzResultRepository extends JpaRepository<QuizzResult, UUID> {

    /** Dernier résultat pour un CV donné */
    Optional<QuizzResult> findTopByCvIdOrderByPassedAtDesc(UUID cvId);

    /** Tous les résultats d'un utilisateur */
    List<QuizzResult> findByUserIdOrderByPassedAtDesc(UUID userId);

    /** Vérifier si un utilisateur a déjà passé le quiz pour un CV */
    boolean existsByCvIdAndUserId(UUID cvId, UUID userId);

    /** Tous les résultats d'une liste de CVs */
    List<QuizzResult> findByCvIdInOrderByPassedAtDesc(List<UUID> cvIds);
    void deleteByCvId(UUID cvId);  // ← AJOUTER CETTE LIGNE

    // Optionnel : méthode pour trouver les quiz par CV
    List<QuizzResult> findByCvId(UUID cvId);
}