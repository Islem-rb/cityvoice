package tn.cityvoice.signalement.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VoiceSessionDebugResponse {

    private String callSid;
    private String identity;
    private boolean descriptionRecordingReceived;
    private boolean locationRecordingReceived;
    private String descriptionTranscription;
    private String locationTranscription;
    private VoiceStructuringResponse structuredSignalement;
    private Long signalementId;
    private boolean completed;
    private String errorMessage;
}
