package tn.cityvoice.evenementservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.evenementservice.dto.request.QrVerificationRequest;
import tn.cityvoice.evenementservice.dto.response.QrVerificationResponse;
import tn.cityvoice.evenementservice.service.QrCodeService;
//@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class QrCodeController {

    private final QrCodeService qrCodeService;

    /**
     * POST /api/qr/verify
     * Body : { "qrToken": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" }
     */
    @PostMapping("/verify")
    public ResponseEntity<QrVerificationResponse> verify(
            @RequestBody QrVerificationRequest request
    ) {
        return ResponseEntity.ok(qrCodeService.verifier(request));
    }
}