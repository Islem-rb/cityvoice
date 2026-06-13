package tn.cityvoice.actualiteservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class Commentaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    private LocalDateTime date;

    private String auteurId;


    @ManyToOne
    @JoinColumn(name = "post_id")
    @JsonIgnore
    private Post post;


    @ManyToOne
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    private Commentaire parent;
}