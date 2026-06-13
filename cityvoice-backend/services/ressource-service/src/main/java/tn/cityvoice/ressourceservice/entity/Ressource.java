package tn.cityvoice.ressourceservice.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ressource")
public class Ressource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    private String type;
    private String etat;
    private Double valeur;
    private Integer dureeVieEstimee;
    private String dateAchat;  // ou LocalDate
    private String imageUrl;
    private String statut;
    private String occupePar;
    private String dateDebutOccupation;
    private String dateFinOccupation;


}