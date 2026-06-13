package tn.cityvoice.userservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PhotoModerationServiceImpl implements PhotoModerationService {

    private final RestTemplate restTemplate;

    // ══════════════════════════════════════════════════════
    // ML Service (primary)
    // ══════════════════════════════════════════════════════
    @Value("${services.ml.photo-moderation.url:http://localhost:8003}")
    private String mlServiceUrl;

    // ══════════════════════════════════════════════════════
    // Groq Vision API (fallback)
    // ══════════════════════════════════════════════════════
    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    private boolean mlServiceAvailable = false;

    @PostConstruct
    public void init() {
        System.out.println("══════════════════════════════════════");
        System.out.println("📸 Photo Moderation Configuration:");
        System.out.println("   ML Service URL: " + mlServiceUrl);

        // Test ML service connection
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    mlServiceUrl + "/health", Map.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                mlServiceAvailable = true;
                System.out.println("   ML Service: ✅ CONNECTED (primary)");
            }
        } catch (Exception e) {
            mlServiceAvailable = false;
            System.out.println("   ML Service: ❌ NOT AVAILABLE");
        }

        // Check Groq fallback
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            System.out.println("   Groq Vision: ✅ CONFIGURED (fallback)");
        } else {
            System.out.println("   Groq Vision: ❌ NO API KEY");
        }

        if (!mlServiceAvailable && (groqApiKey == null || groqApiKey.isBlank())) {
            System.out.println("   ⚠️ NO MODERATION AVAILABLE — photos accepted without check");
        }

        System.out.println("══════════════════════════════════════");
    }

    @Override
    public ModerationResult moderate(String base64Image) {
        // Skip if no image
        if (base64Image == null || base64Image.isBlank()) {
            return new ModerationResult(true, null);
        }

        // Skip very small strings (not real images)
        if (base64Image.length() < 100) {
            return new ModerationResult(false, "Image invalide");
        }

        // ══════════════════════════════════════════════════════
        // Strategy 1: Try ML Service first (fast, local, free)
        // ══════════════════════════════════════════════════════
        ModerationResult mlResult = moderateWithML(base64Image);
        if (mlResult != null) {
            return mlResult;
        }

        // ══════════════════════════════════════════════════════
        // Strategy 2: Fall back to Groq Vision API
        // ══════════════════════════════════════════════════════
        ModerationResult groqResult = moderateWithGroq(base64Image);
        if (groqResult != null) {
            return groqResult;
        }

        // ══════════════════════════════════════════════════════
        // Strategy 3: No moderation available — FAIL OPEN
        // ══════════════════════════════════════════════════════
        System.out.println("⚠️ [MODERATION] No service available — allowing photo");
        return new ModerationResult(true, null);
    }

    // ══════════════════════════════════════════════════════
    // ML SERVICE MODERATION
    // ══════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private ModerationResult moderateWithML(String base64Image) {
        try {
            System.out.println("📸 [ML] Sending to ML service...");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "photo", base64Image,
                    "threshold", 0.5
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    mlServiceUrl + "/moderate",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getBody() == null) {
                System.out.println("⚠️ [ML] Null response");
                return null; // Fall through to Groq
            }

            Boolean safe = (Boolean) response.getBody().get("safe");
            String reason = (String) response.getBody().get("reason");

            System.out.println("📸 [ML] Result: safe=" + safe + ", reason=" + reason);

            mlServiceAvailable = true;

            if (safe == null || safe) {
                System.out.println("✅ [ML] Photo ACCEPTED");
                return new ModerationResult(true, null);
            } else {
                System.out.println("❌ [ML] Photo REJECTED: " + reason);
                return new ModerationResult(false, reason);
            }

        } catch (Exception e) {
            System.out.println("⚠️ [ML] Service unavailable: " + e.getMessage());
            mlServiceAvailable = false;
            return null; // Fall through to Groq
        }
    }

    // ══════════════════════════════════════════════════════
    // GROQ VISION MODERATION (fallback)
    // ══════════════════════════════════════════════════════
    private ModerationResult moderateWithGroq(String base64Image) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            System.out.println("⚠️ [GROQ] No API key configured");
            return null; // No moderation available
        }

        try {
            String imageUrl;
            if (base64Image.startsWith("data:image/")) {
                imageUrl = base64Image;
            } else {
                imageUrl = "data:image/jpeg;base64," + base64Image;
            }

            System.out.println("📸 [GROQ] Sending to Groq Vision (fallback)...");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.2-90b-vision-preview",
                    "max_tokens", 80,
                    "temperature", 0.0,
                    "messages", List.of(
                            Map.of("role", "user", "content", List.of(
                                    Map.of(
                                            "type", "text",
                                            "text", """
                                You are a strict photo moderator for a civic platform.
                                
                                Analyze this profile photo and check if it contains:
                                1. Nudity or sexual content
                                2. Violence, blood, gore, or weapons
                                3. Drugs or illegal substances
                                4. Hate symbols or offensive gestures
                                5. Disturbing or shocking content
                                
                                Reply with ONLY valid JSON, nothing else:
                                {"safe": true}
                                or
                                {"safe": false, "reason": "brief reason in French"}
                                """
                                    ),
                                    Map.of(
                                            "type", "image_url",
                                            "image_url", Map.of("url", imageUrl)
                                    )
                            ))
                    )
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    groqApiUrl, HttpMethod.POST, request, Map.class
            );

            if (response.getBody() == null) {
                System.out.println("⚠️ [GROQ] Null response");
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");

            String text = "";
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message =
                        (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    text = (String) message.get("content");
                }
            }

            System.out.println("📸 [GROQ] Response: " + text);

            if (text == null || text.isBlank()) {
                return new ModerationResult(true, null);
            }

            text = text.trim().toLowerCase();

            if (text.contains("\"safe\"") && text.contains("false")) {
                String reason = "Photo inappropriée pour la plateforme";
                try {
                    if (text.contains("\"reason\"")) {
                        int start = text.indexOf("\"reason\"");
                        String sub = text.substring(start);
                        int colonIdx = sub.indexOf(":");
                        if (colonIdx >= 0) {
                            String afterColon = sub.substring(colonIdx + 1).trim();
                            if (afterColon.startsWith("\"")) {
                                int endQuote = afterColon.indexOf("\"", 1);
                                if (endQuote > 1) {
                                    reason = afterColon.substring(1, endQuote);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                System.out.println("❌ [GROQ] Photo REJECTED: " + reason);
                return new ModerationResult(false, reason);
            }

            System.out.println("✅ [GROQ] Photo ACCEPTED");
            return new ModerationResult(true, null);

        } catch (Exception e) {
            System.out.println("⚠️ [GROQ] Exception: " + e.getMessage());
            return null;
        }
    }
}