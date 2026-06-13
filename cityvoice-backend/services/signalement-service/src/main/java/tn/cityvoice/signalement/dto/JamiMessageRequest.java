package tn.cityvoice.signalement.dto;

import lombok.Data;

/**
 * DTO pour les signalements Jami (via bot de messages texte).
 * Reçu depuis le service jami-bot-service Node.js.
 */
@Data
public class JamiMessageRequest {

    /** ID de session généré par le bot */
    private String sessionId;

    /** URI Jami de l'expéditeur (ex: ring:xxxx ou hash) */
    private String contactUri;

    /** Description du problème (saisie par l'utilisateur) */
    private String description;

    /** Localisation/adresse (saisie par l'utilisateur) */
    private String location;
}
