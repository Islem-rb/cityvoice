package tn.cityvoice.evenementservice.dto.response;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QrVerificationResponse {
    private String statut;          // "VALIDE" | "DEJA_SCANNE" | "INVALIDE"
    private String message;         // texte affiché à l'admin
    private String nomCitoyen;
    private String emailCitoyen;
    private String nomEvenement;
    private String dateInscription;
}
