package tn.cityvoice.ressourceservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "demande_maintenance")
public class DemandeMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ressource_id", nullable = false)
    private Long ressourceId;

    @Column(name = "chef_id", nullable = false)
    private String chefId;

    @Column(nullable = false, length = 500)
    private String motif;

    @Column(nullable = false)
    private String urgence; // BASSE, MOYENNE, HAUTE, CRITIQUE

    @Column(name = "date_remise_souhaitee", nullable = false)
    private LocalDateTime dateRemiseSouhaitee;

    @Column(name = "date_demande", nullable = false)
    private LocalDateTime dateDemande;

    @Column(nullable = false)
    private String statut; // EN_ATTENTE, ACCEPTEE, REFUSEE, TERMINEE

    @Column(name = "maintenance_id")
    private Long maintenanceId;

    @Column(name = "ressource_image_url")
    private String ressourceImageUrl;

    @Column(name = "ressource_matricule")
    private String ressourceMatricule;

    @Column(name = "ressource_nom")
    private String ressourceNom;

    @Column(name = "ressource_type")
    private String ressourceType;

    @Column(name = "technicien_id")
    private String technicienId;
}