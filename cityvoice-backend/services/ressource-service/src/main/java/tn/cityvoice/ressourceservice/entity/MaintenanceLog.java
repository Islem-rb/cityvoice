package tn.cityvoice.ressourceservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "maintenance_log")
public class MaintenanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String typeIntervention;
    private String description;
    private LocalDateTime date;
    private Integer recurrence;
    private String uniteRecurrence;
    private String prochaineMaintenance;
    private LocalDateTime dateFin;   // 🔥 AJOUTER - Date de fin

/*
    @Column(name = "ressource_id")
    private Long ressourceId;

*/

    @Column(name = "ressource_id")
    private Long ressourceId;

    @Column(name = "numero_technicien")
    private String numeroTechnicien;

    @Column(name = "cout")
    private Double cout;

    @Column(name = "technicien_id")
    private String technicienId;  // UUID du technicien
}