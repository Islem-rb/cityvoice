package tn.cityvoice.evenementservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "evenement_notifications")
public class EvenementNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String destinataireId; // UUID citoyen ou admin

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeNotification type;

    @Column(nullable = false)
    private boolean lu = false;

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    // Infos contextuelles optionnelles
    private Long evenementId;
    private String evenementTitre;

    public enum TypeNotification {
        INSCRIPTION,
        PAIEMENT,
        SUGGESTION_ACCEPTEE,
        SUGGESTION_REJETEE,
        EVENEMENT_ANNULE,
        NOUVEAU_PARTICIPANT, // pour admin
        NOUVELLE_SUGGESTION,  // pour admin
        MODIFICATION
    }

    // ── Getters & Setters ─────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDestinataireId() { return destinataireId; }
    public void setDestinataireId(String destinataireId) { this.destinataireId = destinataireId; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public TypeNotification getType() { return type; }
    public void setType(TypeNotification type) { this.type = type; }

    public boolean isLu() { return lu; }
    public void setLu(boolean lu) { this.lu = lu; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public Long getEvenementId() { return evenementId; }
    public void setEvenementId(Long evenementId) { this.evenementId = evenementId; }

    public String getEvenementTitre() { return evenementTitre; }
    public void setEvenementTitre(String evenementTitre) { this.evenementTitre = evenementTitre; }
}