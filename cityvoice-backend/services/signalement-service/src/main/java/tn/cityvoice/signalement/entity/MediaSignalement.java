package tn.cityvoice.signalement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "medias_signalement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaSignalement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signalement_id", nullable = false)
    @ToString.Exclude
    private Signalement signalement;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String url;           // data URI base64 ou URL externe (S3, etc.)

    @Column(nullable = false)
    private String type;          // "image/jpeg", "video/mp4"

    private Long taille;          // en bytes

    @CreationTimestamp
    private LocalDateTime dateUpload;
}
