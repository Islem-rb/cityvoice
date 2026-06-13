package tn.cityvoice.evenementservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TraductionResponse {
    private String texteOriginal;
    private String texteTraduire;
    private String langue;
    private boolean succes;
}