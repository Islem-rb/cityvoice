package tn.cityvoice.projetservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vote_projet")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class VoteProjet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projet_id", nullable = false)
    private Projet projet;
    private String userId;
    @Column(nullable = false)
    private Boolean valeur;
    private LocalDateTime date;
    @PrePersist
    public void prePersist() {
        this.date = LocalDateTime.now();
    }
}