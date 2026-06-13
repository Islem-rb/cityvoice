package tn.cityvoice.actualiteservice.dto;

import tn.cityvoice.actualiteservice.entity.enums.TypeReaction;

public class ReactionRequestDTO {
    private String userId;
    private String userName;
    private String userPhoto;
    private TypeReaction type;

    public String getUserId()           { return userId; }
    public void setUserId(String v)     { this.userId = v; }
    public String getUserName()         { return userName; }
    public void setUserName(String v)   { this.userName = v; }
    public String getUserPhoto()        { return userPhoto; }
    public void setUserPhoto(String v)  { this.userPhoto = v; }
    public TypeReaction getType()       { return type; }
    public void setType(TypeReaction t) { this.type = t; }
}
