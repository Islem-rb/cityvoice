package tn.cityvoice.actualiteservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupMessageDTO {
    private Long   id;
    private Long   groupId;
    private String senderId;
    private String senderName;
    private String senderPhoto;
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sentAt;
}
