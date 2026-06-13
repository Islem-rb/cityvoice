package tn.cityvoice.personnelservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class IAService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public IAService(@Qualifier("ollamaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Score un CV (0-10) via Ollama (modèle phi3).
     * Retourne toujours une Map avec "score" (int) et "justification" (String).
     */
    public Map<String, Object> scoreCv(String description, String cvText) {

        String prompt = """
                Tu es un recruteur senior.

                Description poste:
                %s

                CV:
                %s

                Donne un score entre 0 et 10 + justification.

                Réponds UNIQUEMENT en JSON:
                {"score": number, "justification": string}
                """.formatted(description, cvText);

        Map<String, Object> request = Map.of(
                "model",  "phi3",
                "prompt", prompt,
                "stream", false
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    ollamaBaseUrl + "/api/generate",
                    request,
                    Map.class
            );

            if (response == null || response.get("response") == null) {
                return errorResult("Réponse vide du modèle IA");
            }

            String result = (String) response.get("response");

            result = result.replace("```json", "")
                    .replace("```", "")
                    .trim();

            int start = result.indexOf("{");
            int end   = result.lastIndexOf("}");

            if (start == -1 || end == -1 || end <= start) {
                return errorResult("Réponse IA non parseable: " + result);
            }

            result = result.substring(start, end + 1);

            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(result, Map.class);

            // Jackson peut retourner Integer ou Double selon le JSON → forcer int
            Map<String, Object> normalized = new HashMap<>(parsed);
            Object rawScore = parsed.get("score");
            if (rawScore instanceof Number) {
                normalized.put("score", ((Number) rawScore).intValue());
            } else {
                normalized.put("score", 0);
            }

            return normalized;

        } catch (Exception e) {
            return errorResult("Erreur IA: " + e.getMessage());
        }
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("score",         0);
        err.put("justification", message);
        return err;
    }
}