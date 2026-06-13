package tn.cityvoice.actualiteservice.dto;

import java.time.LocalDateTime;

public class CommentaireResponseDTO {

    private Long   id;
    private String contenu;
    private LocalDateTime date;
    private String auteurId;
    private String auteurNom;
    private Long   postId;
    private Long   parentId;   // null si commentaire racine

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public String getAuteurId() { return auteurId; }
    public void setAuteurId(String auteurId) { this.auteurId = auteurId; }

    public String getAuteurNom() { return auteurNom; }
    public void setAuteurNom(String auteurNom) { this.auteurNom = auteurNom; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
}