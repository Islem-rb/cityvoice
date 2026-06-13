package tn.cityvoice.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor

public class LocationController {

    private static final String TN_API_URL = "https://tn-municipality-api.vercel.app/api/municipalities";

    @GetMapping("/municipalities")
    public ResponseEntity<?> getMunicipalities() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(TN_API_URL, String.class);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(response);
        } catch (Exception e) {
            System.out.println("Location API error: " + e.getMessage());
            return ResponseEntity.status(500).body("[]");
        }
    }
}