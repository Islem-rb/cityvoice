package tn.cityvoice.actualiteservice.live;

import java.time.LocalDateTime;

/**
 * DTO unique pour les commentaires de live — utilisé à la fois en requête
 * (POST /api/live/{roomName}/comments) et en réponse (REST + STOMP).
 *
 * Les champs {@code id} et {@code date} sont remplis par le serveur.
 */
public class LiveCommentDto {

    private Long id;
    private String roomName;
    private String auteurId;
    private String auteurNom;
    private String auteurPhoto;
    private String contenu;
    private LocalDateTime date;

    public LiveCommentDto() {}

    public static LiveCommentDto from(LiveComment c) {
        LiveCommentDto dto = new LiveCommentDto();
        dto.id = c.getId();
        dto.roomName = c.getRoomName();
        dto.auteurId = c.getAuteurId();
        dto.auteurNom = c.getAuteurNom();
        dto.auteurPhoto = c.getAuteurPhoto();
        dto.contenu = c.getContenu();
        dto.date = c.getDate();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getAuteurId() { return auteurId; }
    public void setAuteurId(String auteurId) { this.auteurId = auteurId; }

    public String getAuteurNom() { return auteurNom; }
    public void setAuteurNom(String auteurNom) { this.auteurNom = auteurNom; }

    public String getAuteurPhoto() { return auteurPhoto; }
    public void setAuteurPhoto(String auteurPhoto) { this.auteurPhoto = auteurPhoto; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }
}
