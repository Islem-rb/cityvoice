package tn.cityvoice.evenementservice.dto.request;

import lombok.Data;

@Data
public class QrVerificationRequest {
    private String qrToken;
}