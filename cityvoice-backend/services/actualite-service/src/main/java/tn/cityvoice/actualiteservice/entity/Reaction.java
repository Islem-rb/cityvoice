package tn.cityvoice.actualiteservice.entity;

import jakarta.persistence.*;
import tn.cityvoice.actualiteservice.entity.enums.TypeReaction;
import java.time.LocalDateTime;

@Entity
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;   // UUID du user (String)
    private String userName; // nom affiché (dénormalisé pour éviter appels cross-service)

    @Enumerated(EnumType.STRING)
    private TypeReaction type;

    private LocalDateTime date;

    @ManyToOne
    @JoinColumn(name = "post_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Post post;

    public Long getId()                { return id; }
    public String getUserId()          { return userId; }
    public void setUserId(String v)    { this.userId = v; }
    public String getUserName()        { return userName; }
    public void setUserName(String v)  { this.userName = v; }
    public TypeReaction getType()      { return type; }
    public void setType(TypeReaction t){ this.type = t; }
    public LocalDateTime getDate()     { return date; }
    public void setDate(LocalDateTime d){ this.date = d; }
    public Post getPost()              { return post; }
    public void setPost(Post p)        { this.post = p; }
}
