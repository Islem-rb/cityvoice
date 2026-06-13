package tn.cityvoice.evenementservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "citoyen_interets",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"citoyen_id", "evenement_id"}
        ))
public class CitoyenInteret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String citoyenId;

    @Column(nullable = false)
    private Long evenementId;

    @Column(nullable = false)
    private String typeEvenement; // ← pour l'analyse AI

    @Column(nullable = false)
    private LocalDateTime dateInteret = LocalDateTime.now();

    // ── Getters & Setters ─────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCitoyenId() { return citoyenId; }
    public void setCitoyenId(String citoyenId) { this.citoyenId = citoyenId; }

    public Long getEvenementId() { return evenementId; }
    public void setEvenementId(Long evenementId) { this.evenementId = evenementId; }

    public String getTypeEvenement() { return typeEvenement; }
    public void setTypeEvenement(String typeEvenement) { this.typeEvenement = typeEvenement; }

    public LocalDateTime getDateInteret() { return dateInteret; }
    public void setDateInteret(LocalDateTime dateInteret) { this.dateInteret = dateInteret; }
}