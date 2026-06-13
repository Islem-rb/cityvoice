package tn.cityvoice.evenementservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import tn.cityvoice.evenementservice.enums.TypeEvenement;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String titre;

    private String description;
    private TypeEvenement typeSouhaite;
    private String lieuSouhaite;
    private LocalDate dateSouhaitee;

    @NotNull(message = "L'ID citoyen est obligatoire")
    private String citoyenId;

    @Email(message = "Email invalide")
    private String emailCitoyen;
}