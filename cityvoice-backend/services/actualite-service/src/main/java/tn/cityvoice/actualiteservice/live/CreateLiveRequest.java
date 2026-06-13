package tn.cityvoice.actualiteservice.live;

public class CreateLiveRequest {
    private String title;
    private String username;
    private String userId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
