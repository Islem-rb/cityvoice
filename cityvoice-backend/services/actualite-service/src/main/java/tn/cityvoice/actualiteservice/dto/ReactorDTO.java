package tn.cityvoice.actualiteservice.dto;

public class ReactorDTO {
    private String userId;
    private String userName;
    private String type;

    public ReactorDTO(String userId, String userName, String type) {
        this.userId   = userId;
        this.userName = userName;
        this.type     = type;
    }
    public String getUserId()   { return userId; }
    public String getUserName() { return userName; }
    public String getType()     { return type; }
}
