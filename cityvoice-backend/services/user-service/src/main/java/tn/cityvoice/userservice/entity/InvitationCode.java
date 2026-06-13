package tn.cityvoice.userservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import tn.cityvoice.userservice.entity.enums.Role;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitation_codes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvitationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, unique = true)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Role role;

    @Column(nullable = false)
    boolean used = false;

    @Column(nullable = false)
    LocalDateTime expiresAt;

    UUID createdByAdminId;

    // ── Relation avec l'agent qui a utilisé ce code ────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "used_by_user_id")
    @JsonIgnoreProperties({"password", "resetToken", "resetTokenExpiry",
            "photo", "points", "gouvernorat", "ville",
            "codePostal", "dateInscription", "telephone"})
    @JsonProperty("usedByUser")
    User usedByUser;

    @PrePersist
    void prePersist() {
        if (this.expiresAt == null) {
            this.expiresAt = LocalDateTime.now().plusDays(7);
        }
    }

    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }
}