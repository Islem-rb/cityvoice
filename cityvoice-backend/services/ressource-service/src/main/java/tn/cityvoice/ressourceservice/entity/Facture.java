// tn/cityvoice/ressourceservice/entity/Facture.java
package tn.cityvoice.ressourceservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "factures")
public class Facture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long demandeId;
    private String description;
    private String ressourceNom;
    private String typeIntervention;
    private String dureeEstimee;
    private Double coutTotal;
    private String statut; // EN_ATTENTE, PAYEE, ANNULEE
    private LocalDateTime dateEmission;
    private LocalDateTime datePaiement;
    private String technicienId;
    private Long chefId;

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDemandeId() { return demandeId; }
    public void setDemandeId(Long demandeId) { this.demandeId = demandeId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRessourceNom() { return ressourceNom; }
    public void setRessourceNom(String ressourceNom) { this.ressourceNom = ressourceNom; }

    public String getTypeIntervention() { return typeIntervention; }
    public void setTypeIntervention(String typeIntervention) { this.typeIntervention = typeIntervention; }

    public String getDureeEstimee() { return dureeEstimee; }
    public void setDureeEstimee(String dureeEstimee) { this.dureeEstimee = dureeEstimee; }

    public Double getCoutTotal() { return coutTotal; }
    public void setCoutTotal(Double coutTotal) { this.coutTotal = coutTotal; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public LocalDateTime getDateEmission() { return dateEmission; }
    public void setDateEmission(LocalDateTime dateEmission) { this.dateEmission = dateEmission; }

    public LocalDateTime getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDateTime datePaiement) { this.datePaiement = datePaiement; }

    public String getTechnicienId() { return technicienId; }
    public void setTechnicienId(String technicienId) { this.technicienId = technicienId; }

    public Long getChefId() { return chefId; }
    public void setChefId(Long chefId) { this.chefId = chefId; }
}