package tn.cityvoice.evenementservice.dto.response;

import lombok.*;
import tn.cityvoice.evenementservice.enums.TypeEvenement;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionResponse {

    private Long id;
    private String titre;
    private String description;
    private TypeEvenement typeSouhaite;
    private String lieuSouhaite;
    private LocalDate dateSouhaitee;
    private String citoyenId;
    private String emailCitoyen;
    private String statut;
    private String commentaireAdmin;
    private LocalDateTime soumisLe;
}