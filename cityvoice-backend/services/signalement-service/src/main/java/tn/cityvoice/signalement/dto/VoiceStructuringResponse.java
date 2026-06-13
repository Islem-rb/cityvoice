package tn.cityvoice.signalement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceStructuringResponse {

    private String type;
    private String priorite;
    private String description;
    private String localisation;
}
