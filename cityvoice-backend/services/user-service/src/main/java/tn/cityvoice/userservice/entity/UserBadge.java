package tn.cityvoice.userservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_badges",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @JsonIgnoreProperties({"password","resetToken","resetTokenExpiry","photo",
            "gouvernorat","ville","codePostal","telephone"})
    User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "badge_id", nullable = false)
    Badge badge;

    @Column(nullable = false, updatable = false)
    LocalDateTime obtainedAt;

    @PrePersist
    void prePersist() { this.obtainedAt = LocalDateTime.now(); }
}