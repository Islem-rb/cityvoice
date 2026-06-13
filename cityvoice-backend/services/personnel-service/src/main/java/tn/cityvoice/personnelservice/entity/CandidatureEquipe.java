package tn.cityvoice.personnelservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CandidatureEquipe {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(nullable = false, updatable = false)
    LocalDateTime dateInscription;
    @Column(nullable=false)
    long nbcandidatsA;
    @Column(nullable=false)
    LocalDate dateExpiration;

    @Column(unique = true,nullable = false)
    String statut;
    @Column(nullable = false)
    String Gouvernorat;
    @Column(columnDefinition  ="TEXT")
    @Lob
    String description;
    @Column (nullable = true)
    String fonction;
    @OneToMany(mappedBy = "candidature", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<CvUser> cvs;

    @PrePersist
    public void prePersist() {
        this.dateInscription = LocalDateTime.now();
    }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="equipe_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    Equipe equipe;





}
