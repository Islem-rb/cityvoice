package tn.cityvoice.actualiteservice.dto;

public class CommentaireRequestDTO {

    private String contenu;
    private Long postId;
    private String auteurId;
    private String auteurEmail;
    private String auteurNom;
    private String auteurPhoto;
    private Long parentId;   // null = commentaire racine, sinon = réponse à un commentaire

    public String getAuteurEmail() { return auteurEmail; }
    public void setAuteurEmail(String auteurEmail) { this.auteurEmail = auteurEmail; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getAuteurId() { return auteurId; }
    public void setAuteurId(String auteurId) { this.auteurId = auteurId; }
    public String getAuteurNom() { return auteurNom; }
    public void setAuteurNom(String auteurNom) { this.auteurNom = auteurNom; }
    public String getAuteurPhoto() { return auteurPhoto; }
    public void setAuteurPhoto(String auteurPhoto) { this.auteurPhoto = auteurPhoto; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
}
