package tn.cityvoice.actualiteservice.dto;

import tn.cityvoice.actualiteservice.entity.enums.TypePost;

import java.util.List;

public class PostRequestDTO {

    private String title;
    private String content;
    private TypePost type;
    private List<String> mediaUrls;
    private String auteurEmail;
    private String auteurId;

    public String getAuteurId() { return auteurId; }
    public void setAuteurId(String auteurId) { this.auteurId = auteurId; }

    public String getAuteurEmail() { return auteurEmail; }
    public void setAuteurEmail(String auteurEmail) { this.auteurEmail = auteurEmail; }

    public PostRequestDTO() {
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

    public List<String> getMediaUrls() {
        return mediaUrls;
    }

    public void setMediaUrls(List<String> mediaUrls) {
        this.mediaUrls = mediaUrls;
    }
}