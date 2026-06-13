package tn.cityvoice.evenementservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscriptionRequest {

    @NotNull(message = "L'ID citoyen est obligatoire")
    private String citoyenId;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Email invalide")
    private String email;

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private String telCitoyen;
}