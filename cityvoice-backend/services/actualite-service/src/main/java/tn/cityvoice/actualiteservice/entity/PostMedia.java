package tn.cityvoice.actualiteservice.entity;
import lombok.Data;
import jakarta.persistence.*;
import tn.cityvoice.actualiteservice.entity.enums.TypeMedia;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Data
@Entity
public class PostMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String url;

    @Enumerated(EnumType.STRING)
    private TypeMedia type;

    @ManyToOne
    @JoinColumn(name = "post_id")
    @JsonIgnore
    private Post post;
}