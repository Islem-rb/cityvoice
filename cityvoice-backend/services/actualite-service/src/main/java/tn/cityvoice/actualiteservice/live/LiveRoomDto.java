package tn.cityvoice.actualiteservice.live;

/**
 * DTO renvoyé par l'API Live.
 *
 *  - wsUrl / token : utilisés par livekit-client côté frontend
 *    (ex : room.connect(wsUrl, token))
 *  - roomUrl est conservé pour compatibilité avec l'ancien code Jitsi/Daily
 *    (il contient la même valeur que wsUrl).
 *  - token est null dans les listes publiques et dans l'événement WebSocket
 *    LIVE_STARTED : il n'est renvoyé qu'aux endpoints /create (streamer)
 *    et /{roomName} (viewer) qui génèrent un token frais à chaque appel.
 */
public class LiveRoomDto {
    private String roomName;
    private String roomUrl;
    private String wsUrl;
    private String token;
    private String streamerUsername;
    private String streamerUserId;
    private String title;
    private String startedAt;
    private int viewerCount;

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getRoomUrl() { return roomUrl; }
    public void setRoomUrl(String roomUrl) { this.roomUrl = roomUrl; }

    public String getWsUrl() { return wsUrl; }
    public void setWsUrl(String wsUrl) { this.wsUrl = wsUrl; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getStreamerUsername() { return streamerUsername; }
    public void setStreamerUsername(String streamerUsername) { this.streamerUsername = streamerUsername; }

    public String getStreamerUserId() { return streamerUserId; }
    public void setStreamerUserId(String streamerUserId) { this.streamerUserId = streamerUserId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public int getViewerCount() { return viewerCount; }
    public void setViewerCount(int viewerCount) { this.viewerCount = viewerCount; }
}
