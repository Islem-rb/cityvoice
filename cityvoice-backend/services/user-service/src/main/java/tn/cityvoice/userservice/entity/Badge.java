package tn.cityvoice.userservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import tn.cityvoice.userservice.entity.enums.BadgeCategory;

import java.util.UUID;

@Entity
@Table(name = "badges")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, unique = true)
    String code;        // ex: "PIONEER", "PROFILE_COMPLETE"

    @Column(nullable = false)
    String name;        // ex: "Pionnier"

    @Column(nullable = false)
    String description; // ex: "Premier utilisateur inscrit"

    @Column(nullable = false)
    String emoji;       // ex: "🏅"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    BadgeCategory category;

    @Column(nullable = false)
    int pointsReward;   // points offerts à l'obtention

    @Column(nullable = false)
    String color;       // ex: "#C9973E"
}