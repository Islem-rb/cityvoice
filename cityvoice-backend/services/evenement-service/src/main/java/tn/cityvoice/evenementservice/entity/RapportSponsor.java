package tn.cityvoice.evenementservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rapports_sponsors")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RapportSponsor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate dateRapport;

    private String periode;

    @Column(columnDefinition = "TEXT")
    private String statsJson;

    @Column(columnDefinition = "TEXT")
    private String analyseOllama;

    private String pdfPath;

    @Column(columnDefinition = "TEXT")
    private String pdfBase64;

    @Builder.Default
    private Boolean envoye = false;

    @CreationTimestamp
    private LocalDateTime dateCreation;
}