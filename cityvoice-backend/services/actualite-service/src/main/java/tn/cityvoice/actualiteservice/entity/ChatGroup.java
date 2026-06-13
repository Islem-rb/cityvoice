package tn.cityvoice.actualiteservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Groupe de discussion entre amis.
 * Les IDs des membres sont stockés séparés par des virgules dans memberIds.
 */
@Entity
@Table(name = "chat_groups")
@Data
@NoArgsConstructor
public class ChatGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom affiché du groupe */
    @Column(nullable = false)
    private String name;

    /** Utilisateur qui a créé le groupe */
    @Column(nullable = false)
    private String creatorId;

    /** IDs des membres séparés par virgule ex: "userId1,userId2,userId3" */
    @Column(columnDefinition = "TEXT")
    private String memberIds;

    /** Photo de groupe (URL, optionnelle) */
    private String photoUrl;

    /** IDs des membres bloqués séparés par virgule (bloqués par le créateur) */
    @Column(columnDefinition = "TEXT")
    private String blockedMemberIds;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** Helper : ajouter un membre */
    public void addMember(String userId) {
        if (memberIds == null || memberIds.isBlank()) {
            memberIds = userId;
        } else if (!hasMember(userId)) {
            memberIds = memberIds + "," + userId;
        }
    }

    /** Helper : retirer un membre */
    public void removeMember(String userId) {
        if (memberIds == null) return;
        memberIds = String.join(",",
            java.util.Arrays.stream(memberIds.split(","))
                .filter(id -> !id.trim().equals(userId))
                .toArray(String[]::new)
        );
    }

    /** Helper : vérifier appartenance */
    public boolean hasMember(String userId) {
        if (memberIds == null || memberIds.isBlank()) return false;
        for (String id : memberIds.split(",")) {
            if (id.trim().equals(userId)) return true;
        }
        return false;
    }

    /** Helper : bloquer un membre (le retire + l'ajoute à la liste bloqués) */
    public void blockMember(String userId) {
        removeMember(userId);
        if (blockedMemberIds == null || blockedMemberIds.isBlank()) {
            blockedMemberIds = userId;
        } else if (!isBlocked(userId)) {
            blockedMemberIds = blockedMemberIds + "," + userId;
        }
    }

    /** Helper : débloquer un membre */
    public void unblockMember(String userId) {
        if (blockedMemberIds == null) return;
        blockedMemberIds = String.join(",",
            java.util.Arrays.stream(blockedMemberIds.split(","))
                .filter(id -> !id.trim().equals(userId))
                .toArray(String[]::new)
        );
    }

    /** Helper : vérifier si un user est bloqué */
    public boolean isBlocked(String userId) {
        if (blockedMemberIds == null || blockedMemberIds.isBlank()) return false;
        for (String id : blockedMemberIds.split(",")) {
            if (id.trim().equals(userId)) return true;
        }
        return false;
    }
}
