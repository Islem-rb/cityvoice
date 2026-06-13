package tn.cityvoice.actualiteservice.dto;

import lombok.Data;

@Data
public class ShareRequestDTO {
    /** ID de l'utilisateur qui partage */
    private String sharerId;
    /** Nom affiché (pour la notification) */
    private String sharerNom;
    /** Photo (pour la notification) */
    private String sharerPhoto;
    /** Commentaire optionnel ajouté lors du partage */
    private String commentaire;
}
