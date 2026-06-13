package tn.cityvoice.ressourceservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expediteur_id", nullable = false)
    private String expediteurId;

    @Column(name = "expediteur_nom", nullable = false)
    private String expediteurNom;

    @Column(name = "expediteur_role", nullable = false)
    private String expediteurRole;

    @Column(name = "destinataire_id", nullable = false)
    private String destinataireId;

    @Column(name = "destinataire_role", nullable = false)
    private String destinataireRole;

    @Column(nullable = false, length = 2000)
    private String contenu;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "demande_id")
    private Long demandeId;

    @Column(name = "date_envoi", nullable = false)
    private LocalDateTime dateEnvoi;

    @Column(name = "lu", nullable = false)
    private boolean lu = false;

    @Column(name = "date_lecture")
    private LocalDateTime dateLecture;
}