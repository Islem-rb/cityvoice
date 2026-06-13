package tn.cityvoice.signalement.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tn.cityvoice.signalement.enums.Priorite;
import tn.cityvoice.signalement.enums.StatutSignalement;
import tn.cityvoice.signalement.enums.TypeSignalement;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "signalements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Signalement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ── Type et description ── */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeSignalement type;

    @Column(nullable = false, length = 1000)
    private String description;

    /* ── Localisation ── */
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 500)
    private String adresse;

    /* ── Priorité ── */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priorite prioriteCitoyen = Priorite.MOYENNE;

    @Enumerated(EnumType.STRING)
    private Priorite prioriteIA;

    /* ── Statut ── */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutSignalement statut = StatutSignalement.EN_ATTENTE;

    /* ── Affectation IA ── */
    private String equipeIA;
    private String equipeIALabel;
    private Double delaiEstimeHeures;
    private Double confidenceIA;

    /* ── Citoyen ── */
    @Column(nullable = false)
    private String citoyenId;

    /* ── Source vocale (optionnel) ── */
    @Column(length = 200)
    private String voiceSessionId;   // ID session HybridVoice — permet à l'admin de réécouter

    @Builder.Default
    private Boolean estAnonyme = false;


    @JsonIgnore
    @OneToMany(mappedBy = "signalement", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<MediaSignalement> medias = new ArrayList<>();


    @JsonIgnore
    @OneToMany(mappedBy = "signalement", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<HistoriqueStatut> historique = new ArrayList<>();

    /* ── Votes ── */
    @Builder.Default
    private Integer votes = 0;

    /* ── Dates ── */
    @CreationTimestamp
    @Column(updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateSignalement;

    @UpdateTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dateMiseAJour;

    /* ── URLs des médias (calculé, non persisté) ── */
    @Transient
    @JsonProperty("mediaUrls")
    public List<String> getMediaUrls() {
        if (medias == null) return List.of();
        return medias.stream()
                     .map(MediaSignalement::getUrl)
                     .toList();
    }

    /* ── Helpers ── */
    public void addMedia(MediaSignalement media) {
        media.setSignalement(this);
        this.medias.add(media);
    }

    public void addHistorique(HistoriqueStatut h) {
        h.setSignalement(this);
        this.historique.add(h);
    }
}
