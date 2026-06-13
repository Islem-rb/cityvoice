package tn.cityvoice.projetservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import tn.cityvoice.projetservice.entity.enums.StatutProjet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "projet")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Projet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String categorie;

    @Column(columnDefinition = "LONGTEXT")
    private String image;

    private String location;
    private String tags;

    private String adminId;
    private String adminNom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutProjet statut;

    @Builder.Default
    private int votePour   = 0;
    @Builder.Default
    private int voteContre = 0;
    @Builder.Default
    private int totalVotes = 0;

    private int dureeDays;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private LocalDateTime createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "projet",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    private List<VoteProjet> votes;

    @OneToOne(mappedBy = "projet",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    private CollecteFinancement collecte;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.statut == null) this.statut = StatutProjet.EN_VOTE;
        if (this.dateDebut != null && this.dureeDays > 0) {
            this.dateFin = this.dateDebut.plusDays(this.dureeDays);
        }
    }
}