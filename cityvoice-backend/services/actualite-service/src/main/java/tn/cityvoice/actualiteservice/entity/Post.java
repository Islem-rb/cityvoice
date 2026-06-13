package tn.cityvoice.actualiteservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import tn.cityvoice.actualiteservice.entity.enums.TypePost;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Data
@Entity
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    @Enumerated(EnumType.STRING)
    private TypePost type;

    private boolean epingle;

    private LocalDateTime datePublication;

    private String auteurId;

    // ── Partage (Share) ──────────────────────────────────────
    /** ID du post original partagé (null si c'est un post original) */
    private Long sharedFromPostId;

    /** auteurId de l'auteur original (dénormalisé) */
    private String sharedFromAuteurId;

    /** Nom de l'auteur original (dénormalisé pour affichage) */
    private String sharedFromAuteurNom;

    /** Contenu du post original (dénormalisé pour affichage sans requête) */
    @Column(columnDefinition = "TEXT")
    private String sharedFromContent;

    /** Titre du post original (dénormalisé) */
    private String sharedFromTitre;

    /** URLs média du post original (dénormalisées) */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "shared_post_media_urls", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "url", columnDefinition = "TEXT")
    private List<String> sharedFromMediaUrls;

    /** Nombre de partages de ce post */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private int shareCount = 0;

    // Relations

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Commentaire> commentaires;


    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Reaction> reactions;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<PostMedia> medias;
}