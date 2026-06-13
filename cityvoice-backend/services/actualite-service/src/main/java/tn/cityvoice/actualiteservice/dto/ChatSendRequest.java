package tn.cityvoice.actualiteservice.dto;

import lombok.Data;

@Data
public class ChatSendRequest {
    private String senderId;
    private String receiverId;
    private String content;
}
