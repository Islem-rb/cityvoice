package tn.cityvoice.projetservice.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.cityvoice.projetservice.service.OllamaService;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final OllamaService ollamaService;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    // ── Price prediction (existing, unchanged) ─────────
    @PostMapping("/predict-price")
    public ResponseEntity<Map<String, Object>> predictPrice(
            @RequestBody Map<String, String> req) {

        String description = req.getOrDefault("description", "");
        String categorie   = req.getOrDefault("categorie",   "");
        String location    = req.getOrDefault("location",    "");
        String titre       = req.getOrDefault("titre",       "");

        if (description.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Description requise"));
        }

        String prompt = String.format("""
            You are a cost estimation expert for urban projects in Tunisia.
            Estimate the total cost in Tunisian Dinars (TND/DT).
            Project title: %s
            Category: %s
            Location: %s
            Description: %s
            Reference prices in Tunisia (TND):
            - Road repair per km: 200,000 - 500,000 DT
            - Small park: 50,000 - 150,000 DT
            - School (8 classes): 1,500,000 - 3,000,000 DT
            - Street lighting per km: 80,000 - 200,000 DT
            Output ONLY this JSON:
            {"montant":450000,"fourchetteBasse":380000,"fourchetteHaute":520000,"justification":"Une phrase courte en français."}
            """, titre, categorie, location, description);

        try {
            String raw     = ollamaService.generateContent(prompt);
            String cleaned = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
            String json    = extractJsonObject(cleaned);
            if (json == null) return ResponseEntity.ok(fallbackPrice());

            Map<String, Object> parsed = objectMapper.readValue(
                    json, new TypeReference<>() {});
            if (!parsed.containsKey("montant"))
                return ResponseEntity.ok(fallbackPrice());

            long montant = ((Number) parsed.get("montant")).longValue();
            long basse   = parsed.containsKey("fourchetteBasse")
                    ? ((Number) parsed.get("fourchetteBasse")).longValue()
                    : Math.round(montant * 0.85);
            long haute   = parsed.containsKey("fourchetteHaute")
                    ? ((Number) parsed.get("fourchetteHaute")).longValue()
                    : Math.round(montant * 1.15);
            String justif = parsed.getOrDefault("justification",
                    "Estimation basée sur les coûts locaux.").toString();

            montant = Math.min(Math.max(montant, 10_000), 50_000_000);
            basse   = Math.min(Math.max(basse,   10_000), montant);
            haute   = Math.max(Math.min(haute, 50_000_000), montant);

            return ResponseEntity.ok(Map.of("result", Map.of(
                    "montant", montant, "fourchetteBasse", basse,
                    "fourchetteHaute", haute, "justification", justif)));

        } catch (Exception e) {
            log.error("Price predict error: {}", e.getMessage());
            return ResponseEntity.ok(fallbackPrice());
        }
    }

    // ── AI 1: Image validation ─────────────────────────
    @PostMapping("/validate-image")
    public ResponseEntity<Map<String, Object>> validateImage(
            @RequestBody Map<String, String> req) {

        String imageBase64 = req.getOrDefault("image",       "");
        String description = req.getOrDefault("description", "");
        String titre       = req.getOrDefault("titre",       "");

        if (imageBase64.isBlank() || description.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Image and description required"));
        }

        log.info("Validating image for project: {}", titre);

        String prompt = String.format("""
        Look at this image. Does it match this project?
        Title: %s
        Description: %s
        
        Rules:
        - If the image is a drawing, cartoon, anime, or not a real photograph, it is NOT a valid match for a real urban project. = NO MATCH
        - Cat, dog, animal,flowers photo for construction/urban project = NO MATCH
        - Random food photo for urban project, random internet memes = NO MATCH
        - Building, road, park, construction site, urban scene = MATCH
        - Any image related to the project theme = MATCH
        
        Respond ONLY with JSON:
        {"matches":true,"confidence":"high","reason":"L image correspond au projet."}
        """, titre, description.substring(0, Math.min(description.length(), 200)));

        try {
            String raw     = ollamaService.generateVision(prompt, imageBase64);
            String cleaned = raw.replaceAll("(?s)<think>.*?</think>", "")
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            log.info("Vision cleaned: {}", cleaned);
            String json = extractJsonObject(cleaned);

            if (json != null) {
                Map<String, Object> parsed = objectMapper.readValue(
                        json, new TypeReference<>() {});
                log.info("Validation result: matches={}", parsed.get("matches"));
                return ResponseEntity.ok(parsed);
            }

            log.warn("No JSON found in vision response: {}", cleaned);
        } catch (Exception e) {
            log.error("Image validation EXCEPTION: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok(Map.of(
                "matches",    false,
                "confidence", "low",
                "reason",     "Validation impossible — réessayez ou changez l'image."));
    }

    // ── AI 2: Project suggestion ───────────────────────
    @PostMapping("/suggest-project")
    public ResponseEntity<Map<String, Object>> suggestProject(
            @RequestBody Map<String, String> req) {

        String gouvernorat = req.getOrDefault("gouvernorat", "");
        if (gouvernorat.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Gouvernorat requis"));
        }

        log.info("Suggesting project for: {}", gouvernorat);

        // Very simple prompt — less likely to confuse the model
        String prompt = "Urban project expert Tunisia. "
                + "Suggest one project for " + gouvernorat + " governorate. "
                + "Choose category from: Infrastructure, Espaces verts, Culture, Mobilite, Autre. "
                + "Output ONLY this exact JSON format with no other text: "
                + "{\"titre\":\"Renovation des routes\","
                + "\"description\":\"Description courte en francais.\","
                + "\"categorie\":\"Infrastructure\","
                + "\"montantEstime\":500000,"
                + "\"justification\":\"Raison courte en francais.\"}";

        try {
            String raw = ollamaService.generateContent(prompt);
            log.info("Suggest raw response: {}", raw);

            String cleaned = raw
                    .replaceAll("(?s)<think>.*?</think>", "")
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            log.info("Suggest cleaned: {}", cleaned);

            String json = extractJsonObject(cleaned);
            log.info("Suggest extracted JSON: {}", json);

            if (json != null) {
                Map<String, Object> parsed = objectMapper.readValue(
                        json, new TypeReference<>() {});
                parsed.put("gouvernorat", gouvernorat);
                return ResponseEntity.ok(Map.of("result", parsed));
            }

            // JSON parsing failed — return smart fallback
            log.warn("No JSON found, returning fallback for {}", gouvernorat);
            return ResponseEntity.ok(Map.of("result", buildFallbackSuggestion(gouvernorat)));

        } catch (Exception e) {
            log.error("Suggest EXCEPTION for {}: {}", gouvernorat, e.getMessage(), e);
            // Return fallback instead of 500
            return ResponseEntity.ok(Map.of("result", buildFallbackSuggestion(gouvernorat)));
        }
    }

    private Map<String, Object> buildFallbackSuggestion(String gouvernorat) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("titre",         "Réhabilitation des voies urbaines de " + gouvernorat);
        s.put("description",   "Réparation et modernisation des routes principales et secondaires "
                + "du gouvernorat de " + gouvernorat
                + ". Inclut la signalisation, l'éclairage et les trottoirs.");
        s.put("categorie",     "Infrastructure");
        s.put("montantEstime", 750000);
        s.put("justification", "Les infrastructures routières sont prioritaires pour améliorer "
                + "la qualité de vie à " + gouvernorat + ".");
        s.put("gouvernorat",   gouvernorat);
        return s;
    }
    // ── Helpers ───────────────────────────────────────
    private Map<String, Object> fallbackPrice() {
        return Map.of("result", Map.of(
                "montant", 450000, "fourchetteBasse", 380000,
                "fourchetteHaute", 520000,
                "justification", "Estimation par défaut."));
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start == -1) return null;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '{') depth++;
            else if (text.charAt(i) == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return null;
    }
}