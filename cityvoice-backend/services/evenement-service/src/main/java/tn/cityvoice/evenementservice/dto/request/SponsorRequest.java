package tn.cityvoice.evenementservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsorRequest {

    @NotBlank(message = "Le nom de l'entreprise est obligatoire")
    private String nomEntreprise;

    private String logoUrl;
    private String siteWeb;
    private String niveauSponsorat;
    private BigDecimal montantSponsorat;
    @Pattern(
            regexp = "^$|TELECOM|BANQUE|TECH|GRANDE_SURFACE|SANTE|EDUCATION|ENERGIE|AUTRE",
            message = "Secteur d'activité invalide"
    )
    @NotBlank(message = "Le secteur d'activité est obligatoire")
    private String secteurActivite;

    @NotBlank(message = "La taille de l'entreprise est obligatoire")
    @Pattern(
            regexp = "PME|GRANDE_ENTREPRISE|MULTINATIONALE",
            message = "Taille d'entreprise invalide"
    )
    private String tailleEntreprise;
    //^$|
    @NotBlank(message = "La zone géographique est obligatoire")
    @Pattern(
            regexp = "TUNIS|SFAX|SOUSSE|NATIONAL|INTERNATIONAL",
            message = "Zone géographique invalide"
    )
    private String zoneGeographique;

    private Boolean actifSponsoring = true;
}