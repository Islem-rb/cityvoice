package tn.cityvoice.evenementservice.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsorResponse {
    private Long id;
    private String nomEntreprise;
    private String logoUrl;
    private String siteWeb;
    private String niveauSponsorat;
    private BigDecimal montantSponsorat;
    private List<Long> evenementIds;
    private String secteurActivite;
    private String tailleEntreprise;
    private String zoneGeographique;
    private Boolean actifSponsoring;
}