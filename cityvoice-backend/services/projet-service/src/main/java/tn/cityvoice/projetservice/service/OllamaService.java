package tn.cityvoice.projetservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class OllamaService {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:qwen3:8b}")
    private String model;

    @Value("${ollama.vision-model:qwen3-vl:8b}")
    private String visionModel;

    // ← Long timeout: 5 min connect, 10 min read (models are slow)
    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(60))
            .setReadTimeout(Duration.ofMinutes(15))
            .build();

    // ── Text generation ────────────────────────────────
    public String generateContent(String prompt) {
        String url = ollamaBaseUrl + "/api/generate";

        // Use HashMap — Map.of() has issues with arrays
        Map<String, Object> options = new HashMap<>();
        options.put("temperature",  0.1);
        options.put("num_predict",  400);
        options.put("stop",         new String[]{"</think>", "\n\n\n"});

        Map<String, Object> request = new HashMap<>();
        request.put("model",   model);
        request.put("system",  "You output ONLY valid JSON. No text before or after. No markdown. No thinking.");
        request.put("prompt",  prompt + "\n/no_think");
        request.put("stream",  false);
        request.put("options", options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("Calling Ollama text model: {}", model);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, headers), Map.class
            );
            if (response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null) {
                String text = (String) response.getBody().get("response");
                log.info("Ollama text response length: {}",
                        text != null ? text.length() : 0);
                return text != null ? text : "";
            }
            throw new RuntimeException("Ollama status: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Ollama text FAILED: {}", e.getMessage());
            throw new RuntimeException("Ollama failed: " + e.getMessage(), e);
        }
    }

    // ── Vision: analyze image ─────────────────────────
    public String generateVision(String prompt, String imageBase64) {
        String url = ollamaBaseUrl + "/api/generate";

        // Strip data URL prefix if present
        String base64 = imageBase64;
        if (imageBase64.contains(",")) {
            base64 = imageBase64.split(",")[1];
        }

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.1);
        options.put("num_predict", 300);

        List<String> images = new ArrayList<>();
        images.add(base64);

        Map<String, Object> request = new HashMap<>();
        request.put("model",   visionModel);
        request.put("system",  "You output ONLY valid JSON. No text before or after. No markdown.");
        request.put("prompt",  prompt);
        request.put("images",  images);
        request.put("stream",  false);
        request.put("options", options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("Calling Ollama vision model: {}", visionModel);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, headers), Map.class
            );
            if (response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null) {
                String text = (String) response.getBody().get("response");
                log.info("Vision raw response: {}", text);
                return text != null ? text : "";
            }
            throw new RuntimeException("Vision status: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Ollama vision FAILED: {}", e.getMessage());
            throw new RuntimeException("Vision failed: " + e.getMessage(), e);
        }
    }
}