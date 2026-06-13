package tn.cityvoice.userservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import tn.cityvoice.userservice.entity.enums.AgentStatus;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.entity.enums.TrustLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    String nom;

    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false)
    String password;

    String telephone;

    // ── Localisation ───────────────────────────
    String gouvernorat;
    String ville;
    String codePostal;

    // ── Photo stockée en base64 ───────────────────────────
    @Column(columnDefinition = "LONGTEXT")
    String photo;

    @Column(nullable = false)
    int points = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Role role;

    @Column(nullable = false, updatable = false)
    LocalDateTime dateInscription;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    List<UserBadge> badges = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    List<PointTransaction> pointTransactions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TrustLevel trustLevel = TrustLevel.NOUVEAU;

    // ── Reset password ────────────────────────────────────
    String resetToken;
    LocalDateTime resetTokenExpiry;

    // ── Email verification ────────────────────────────────────
    @Column(nullable = false)
    boolean emailVerified = false;
    String emailVerificationToken;

    // ── Notifications WhatsApp (via CallMeBot) ───────────────
    @Column(nullable = false)
    boolean whatsappNotifs = false;

    /** Clé API personnelle CallMeBot — fournie par l'utilisateur après activation */
    String callmebotApiKey;

    // ── Notifications SMS (canal alternatif) ─────────────────
    /** Si activé, l'utilisateur reçoit un SMS à chaque changement de statut
     *  (en plus ou à la place du WhatsApp selon sa préférence). */
    @Column(nullable = false)
    boolean smsNotifs = false;

    // ── Ban/Unban ────────────────────────────────────
    @Column(nullable = false)
    boolean banned = false;
    String banReason;

    // ── Streak system ────────────────────────────────────
    @Column(nullable = false)
    int loginStreak = 0;
    LocalDate lastLoginDate;

    // Statut agent (seulement pour les rôles agents)
    @Enumerated(EnumType.STRING)
    AgentStatus agentStatus = AgentStatus.DISPONIBLE;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "is_online")
    private boolean isOnline = false;

    @PrePersist
    void prePersist() {
        this.dateInscription = LocalDateTime.now();
        if (this.role   == null) this.role   = Role.CITOYEN;
        if (this.points == 0)    this.points = 0;
    }
}