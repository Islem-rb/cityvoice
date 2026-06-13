package tn.cityvoice.personnelservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MembreEquipe {
    @Id
    @GeneratedValue
    UUID id;


    /**
     * Identifiant du user (user-service). Nullable pour compatibilité historique.
     * Utilisé pour empêcher qu'un user soit recruté dans plusieurs équipes.
     */
    @Column
    UUID userId;

    @Column(nullable = false)
    String nom;
    @Column(nullable = false)
    String prenom;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Fonction fonction;
    @Column(nullable=false)
    LocalDateTime dateAdhesion;
    @Column
    String email;
    @Column
    String telephone;
    @Column(columnDefinition = "LONGTEXT")
    String photo;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "cv_id",nullable = true)
    private CV cv;

    @PrePersist
    void prePersist() {
        dateAdhesion = LocalDateTime.now();
    }



}
