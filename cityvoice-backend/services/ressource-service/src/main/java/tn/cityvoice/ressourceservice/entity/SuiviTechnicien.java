package tn.cityvoice.ressourceservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "suivi_technicien")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuiviTechnicien {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "technicien_id", nullable = false)
    private byte[] technicienId;  // BINARY(16) - UUID du technicien

    @Column(name = "maintenance_id", nullable = false)
    private Long maintenanceId;  // ID de la maintenance (maintenance_log)

    @Column(nullable = false)
    private String statut;  // EN_SERVICE, DEJEUNER, PAUSE_WC, REUNION, HORS_LIGNE, TERMINE

    @Column(nullable = false)
    private LocalDateTime debut;

    private LocalDateTime fin;

    @Column(name = "duree_secondes")
    private Integer dureeSecondes;

    @Column(name = "est_paye")
    private Boolean estPaye = false;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation = LocalDateTime.now();
}