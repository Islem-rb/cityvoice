package tn.cityvoice.userservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import tn.cityvoice.userservice.entity.enums.PointReason;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "point_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    User user;

    @Column(nullable = false)
    int points;  // positif = gain, négatif = perte

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PointReason reason;

    String description;  // message personnalisé optionnel

    @Column(nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}