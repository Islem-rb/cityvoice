package tn.cityvoice.signalement.repository;

import tn.cityvoice.signalement.entity.ContratTravail;
import tn.cityvoice.signalement.enums.StatutContrat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContratTravailRepository extends JpaRepository<ContratTravail, Long> {

    /** Tous les contrats d'un signalement (ordonnés par tentative) */
    List<ContratTravail> findBySignalementIdOrderByTentativeAsc(Long signalementId);

    /** Contrat actif (dernier en date) pour un signalement */
    Optional<ContratTravail> findTopBySignalementIdOrderByTentativeDesc(Long signalementId);

    /** Contrats en attente de signature (pour le dashboard admin) */
    List<ContratTravail> findByStatutOrderByDateCreationDesc(StatutContrat statut);

    /** Par numéro de contrat unique */
    Optional<ContratTravail> findByNumeroContrat(String numeroContrat);

    /** Contrats actifs d'une équipe spécifique */
    List<ContratTravail> findByEquipeCodeAndStatut(String equipeCode, StatutContrat statut);

    /** Contrats assignés à un chef d'équipe (par son userId) */
    List<ContratTravail> findByChefEquipeIdOrderByDateCreationDesc(String chefEquipeId);

    /** Contrats en attente d'une équipe (pour routing vers le bon chef) */
    List<ContratTravail> findByEquipeCodeAndStatutOrderByDateCreationDesc(String equipeCode, StatutContrat statut);

    /** Tous les contrats d'une équipe (tous statuts) — fallback quand chefId n'est pas lié */
    List<ContratTravail> findByEquipeCodeOrderByDateCreationDesc(String equipeCode);
}
