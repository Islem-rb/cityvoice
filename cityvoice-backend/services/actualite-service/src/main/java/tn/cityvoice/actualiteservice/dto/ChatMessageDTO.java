package tn.cityvoice.actualiteservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private Long id;
    private String senderId;
    private String receiverId;
    private String content;

    // Forcer ISO string "2026-03-31T00:47:00" au lieu du tableau [2026,3,31,...]
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sentAt;

    // Forcer la clé JSON "isRead" (Lombok+boolean génère "read" par défaut)
    @JsonProperty("isRead")
    private boolean isRead;
}
