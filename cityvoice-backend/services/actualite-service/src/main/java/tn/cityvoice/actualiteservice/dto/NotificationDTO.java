package tn.cityvoice.actualiteservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationDTO {
    private Long   id;
    private String recipientId;
    private String actorId;
    private String actorName;
    private String actorPhoto;
    private String type;
    private String message;
    private Long   postId;
    private boolean read;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
