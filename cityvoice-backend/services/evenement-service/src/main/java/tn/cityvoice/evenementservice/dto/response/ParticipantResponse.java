package tn.cityvoice.evenementservice.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantResponse {
    private Long id;
    private String citoyenId;
    private String emailCitoyen;
    private String nomCitoyen;
    private String qrToken;
    private String statutPresence;
    private String inscritLe;
    private Long evenementId;
}