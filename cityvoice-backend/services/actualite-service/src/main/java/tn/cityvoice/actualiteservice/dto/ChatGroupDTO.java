package tn.cityvoice.actualiteservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatGroupDTO {
    private Long   id;
    private String name;
    private String creatorId;
    private List<String> memberIds;
    private List<String> blockedMemberIds;
    private String photoUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
