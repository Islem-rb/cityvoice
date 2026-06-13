package tn.cityvoice.signalement.service;

import tn.cityvoice.signalement.dto.ContratReponseRequest;
import tn.cityvoice.signalement.entity.ContratTravail;
import tn.cityvoice.signalement.entity.Signalement;

import java.util.List;
import java.util.Optional;

/**
 * Contrat de service pour la gestion des contrats de travail.
 * Pattern cours : Interface + ServiceImpl (sans DTO de réponse).
 */
public interface IContratTravailService {

    /**
     * Génère un contrat de travail pour ce signalement.
     * @param sig          le signalement dont l'équipeIA est déjà renseignée
     * @param chefEquipeId userId du chef d'équipe (peut être null si non trouvé)
     */
    ContratTravail genererContrat(Signalement sig, String chefEquipeId);

    ContratTravail accepterContrat(Long contratId, ContratReponseRequest req, String chefEquipeId);

    ContratTravail refuserContrat(Long contratId, ContratReponseRequest req, String chefEquipeId);

    ContratTravail getById(Long id);

    ContratTravail getByNumero(String numero);

    Optional<ContratTravail> getContratActifParSignalement(Long sigId);

    List<ContratTravail> getHistoriqueParSignalement(Long sigId);

    List<ContratTravail> getContratsEnAttente();

    List<ContratTravail> getTousLesContrats();

    /** Contrats destinés à un chef d'équipe spécifique (EN_ATTENTE + ses ACCEPTE) */
    List<ContratTravail> getContratsParChef(String chefEquipeId);

    /** Contrats en attente pour une équipe donnée (routing automatique vers chef) */
    List<ContratTravail> getContratsEnAttenteParEquipe(String equipeCode);

    /** Tous les contrats d'une équipe (utilisé quand le chef n'a pas de userId lié) */
    List<ContratTravail> getContratsParEquipe(String equipeCode);
}
