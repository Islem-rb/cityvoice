package tn.cityvoice.personnelservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quiz_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuizzResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    UUID userId;

    @Column(nullable = false)
    UUID cvId;               // ← AJOUTÉ : lien vers le CV du candidat

    @Column(nullable = false)
    String fonction;         // poste / fonction de la candidature

    @Column(nullable = false)
    int score;               // 0-10

    @Column(nullable = false)
    int totalQuestions;      // toujours 10

    @Column(nullable = false)
    boolean timeExpired;     // true si le temps était écoulé

    @Column(nullable = false)
    LocalDateTime passedAt;

    @PrePersist
    void prePersist() {
        this.passedAt = LocalDateTime.now();
    }
}