package tn.cityvoice.actualiteservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "friendships",
       uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;   // UUID du demandeur

    @Column(name = "addressee_id", nullable = false)
    private String addresseeId;   // UUID du destinataire

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum FriendshipStatus {
        PENDING, ACCEPTED, REJECTED, BLOCKED
    }
}
