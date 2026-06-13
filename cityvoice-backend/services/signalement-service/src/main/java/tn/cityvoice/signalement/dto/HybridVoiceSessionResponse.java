package tn.cityvoice.signalement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridVoiceSessionResponse {
    private String sessionId;
    private String source; // "web" ou "jami"
    private boolean descriptionReceived;
    private boolean locationReceived;
    private String descriptionTranscription;
    private String locationTranscription;
    private Map<String, Object> structuredData;
    private Long signalementId;
    private boolean completed;
    private String errorMessage;
    private LocalDateTime createdAt;
}
