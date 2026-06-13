package tn.cityvoice.evenementservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sponsors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomEntreprise;

    private String logoUrl;
    private String siteWeb;
    // ── Profil sponsor ────────────────────────────────
    private String secteurActivite;  // TELECOM, BANQUE, TECH, GRANDE_SURFACE, SANTE, EDUCATION, ENERGIE, AUTRE
    private String tailleEntreprise; // PME, GRANDE_ENTREPRISE, MULTINATIONALE
    private String zoneGeographique; // TUNIS, SFAX, SOUSSE, NATIONAL, INTERNATIONAL

    @Builder.Default
    private Boolean actifSponsoring = true;
}