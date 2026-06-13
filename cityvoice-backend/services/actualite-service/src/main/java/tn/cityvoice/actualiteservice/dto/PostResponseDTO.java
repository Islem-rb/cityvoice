package tn.cityvoice.actualiteservice.dto;

import tn.cityvoice.actualiteservice.entity.enums.TypePost;

import java.time.LocalDateTime;
import java.util.List;

public class PostResponseDTO {

    private Long id;
    private String title;
    private String content;
    private TypePost type;
    private LocalDateTime createdAt;
    private List<String> mediaUrls;
    private String auteurId;

    // Share fields
    private Long   sharedFromPostId;
    private String sharedFromAuteurId;
    private String sharedFromAuteurNom;
    private String sharedFromContent;
    private String sharedFromTitre;
    private List<String> sharedFromMediaUrls;
    private int    shareCount;

    public PostResponseDTO() {}

    public Long getSharedFromPostId() { return sharedFromPostId; }
    public void setSharedFromPostId(Long v) { this.sharedFromPostId = v; }
    public String getSharedFromAuteurId() { return sharedFromAuteurId; }
    public void setSharedFromAuteurId(String v) { this.sharedFromAuteurId = v; }
    public String getSharedFromAuteurNom() { return sharedFromAuteurNom; }
    public void setSharedFromAuteurNom(String v) { this.sharedFromAuteurNom = v; }
    public String getSharedFromContent() { return sharedFromContent; }
    public void setSharedFromContent(String v) { this.sharedFromContent = v; }
    public String getSharedFromTitre() { return sharedFromTitre; }
    public void setSharedFromTitre(String v) { this.sharedFromTitre = v; }
    public List<String> getSharedFromMediaUrls() { return sharedFromMediaUrls; }
    public void setSharedFromMediaUrls(List<String> v) { this.sharedFromMediaUrls = v; }
    public int getShareCount() { return shareCount; }
    public void setShareCount(int v) { this.shareCount = v; }

    public String getAuteurId() { return auteurId; }
    public void setAuteurId(String auteurId) { this.auteurId = auteurId; }
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public TypePost getType() {
        return type;
    }

    public void setType(TypePost type) {
        this.type = type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getMediaUrls() {
        return mediaUrls;
    }

    public void setMediaUrls(List<String> mediaUrls) {
        this.mediaUrls = mediaUrls;
    }
}