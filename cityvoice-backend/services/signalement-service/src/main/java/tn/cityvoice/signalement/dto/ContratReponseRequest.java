package tn.cityvoice.signalement.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContratReponseRequest {

    /** base64 de l'image de signature canvas (requis pour accepter) */
    private String signatureBase64;

    /** Motif de refus (requis si action = "refuser") */
    @Size(max = 500)
    private String motifRefus;
}
