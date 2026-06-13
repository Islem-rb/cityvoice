package tn.cityvoice.signalement.dto;

import lombok.Data;

/**
 * DTO pour l'upload d'audio vocal (WebRTC navigateur ou Jami).
 * L'audio est envoyé dans le body JSON (pas en query param)
 * pour éviter la limite de taille des URLs (414 Too Long).
 */
@Data
public class VoiceUploadRequest {

    /** ID de session (optionnel — généré côté serveur si absent) */
    private String sessionId;

    /** Source: "web" (WebRTC) ou "jami" */
    private String source;

    /** Étape: "description" ou "location" */
    private String step;

    /** Audio encodé en Base64 (audio/webm;codecs=opus) */
    private String audioBase64;

    /** ID de l'utilisateur connecté (pour associer le signalement au bon citoyen) */
    private String userId;
}
