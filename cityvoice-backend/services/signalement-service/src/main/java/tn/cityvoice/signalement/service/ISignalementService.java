package tn.cityvoice.signalement.service;

import tn.cityvoice.signalement.dto.SignalementRequest;
import tn.cityvoice.signalement.dto.StatutUpdateRequest;
import tn.cityvoice.signalement.entity.Signalement;
import tn.cityvoice.signalement.enums.StatutSignalement;

import java.util.List;
import java.util.Map;

/**
 * Contrat de service pour la gestion des signalements.
 * Pattern cours : Interface + ServiceImpl (sans DTO de réponse).
 */
public interface ISignalementService {

    Signalement create(SignalementRequest req, String citoyenId);

    void enrichWithAI(Signalement sig, String imageBase64);

    Signalement changerStatut(Long id, StatutUpdateRequest req, String operateurId);

    /** Correction manuelle de la localisation (admin après écoute vocale) */
    Signalement updateLocalisation(Long id, Double latitude, Double longitude, String adresse);

    void delete(Long id, String role);

    Signalement voter(Long id);

    Signalement getById(Long id);

    List<Signalement> getMesSignalements(String citoyenId);

    List<Signalement> getByStatut(StatutSignalement statut);

    List<Signalement> getByProximite(double lat, double lng, double km);

    List<Signalement> getAll();

    Map<String, Long> getStats();

    /**
     * Chef d'équipe marque un signalement comme résolu avec photo "après".
     * LLaVA compare la photo originale et la photo de résolution.
     * Retourne le signalement mis à jour + rapport de vérification IA.
     */
    Map<String, Object> resoudreParChef(Long id, String photoApres, String commentaire, String chefId);
}
