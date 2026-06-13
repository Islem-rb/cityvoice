package tn.cityvoice.actualiteservice.live;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Commentaire posté pendant un live (type "chat du live").
 *
 * On NE réutilise PAS l'entité {@link tn.cityvoice.actualiteservice.entity.Commentaire}
 * parce que celle-ci est strictement liée à un Post (colonne post_id NOT NULL en DB).
 * Les lives n'existent qu'en mémoire (voir {@link LiveService}) — il n'y a donc pas
 * de Post à attacher.
 *
 * Les commentaires sont persistés pour pouvoir afficher l'historique aux nouveaux
 * viewers qui rejoignent le live en cours de route, et sont diffusés en temps réel
 * via STOMP sur /topic/live.{roomName}.comments.
 */
@Data
@Entity
@Table(name = "live_comment", indexes = {
        @Index(name = "idx_live_comment_room", columnList = "roomName")
})
public class LiveComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom de la room LiveKit (cf. LiveRoomDto#getRoomName()). */
    @Column(nullable = false, length = 64)
    private String roomName;

    /** ID de l'utilisateur qui commente (peut être null si anonyme). */
    @Column(length = 64)
    private String auteurId;

    /** Nom affiché (ex: "Tassnim" ou "Spectateur"). */
    @Column(length = 120)
    private String auteurNom;

    /** URL de la photo de profil (optionnelle). */
    @Column(length = 500)
    private String auteurPhoto;

    /** Contenu du message — limité à 500 caractères côté controller. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String contenu;

    @Column(nullable = false)
    private LocalDateTime date;
}
