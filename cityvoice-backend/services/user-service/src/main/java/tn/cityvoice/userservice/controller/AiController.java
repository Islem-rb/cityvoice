package tn.cityvoice.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.userservice.service.NameModerationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final NameModerationService nameModerationService;

    @Value("${groq.api.key}")
    String apiKey;

    @Value("${groq.api.url}")
    String apiUrl;

    @PostMapping("/suggest")
    public ResponseEntity<?> suggest(@RequestBody Map<String, String> body) {
        String prompt = body.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body("Prompt requis");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);  // ← Groq utilise Bearer token

            Map<String, Object> requestBody = Map.of(
                    "model",       "llama-3.1-8b-instant",
                    "max_tokens",  300,
                    "temperature", 0.8,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    )
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, request, Map.class
            );

            // Structure OpenAI-compatible
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");

            String text = "Continuez à améliorer votre profil !";

            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message =
                        (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    text = (String) message.get("content");
                }
            }

            return ResponseEntity.ok(Map.of("suggestion", text));

        } catch (Exception e) {
            System.out.println("AI error: " + e.getMessage());
            return ResponseEntity.ok(Map.of("suggestion",
                    generateFallback(prompt)));
        }
    }

    private String generateFallback(String prompt) {
        if (prompt.contains("complet") && prompt.contains("100")) {
            return "Bravo, votre profil est complet ! 🎉 Vous êtes prêt à contribuer pleinement à l'amélioration de votre ville.";
        }
        if (prompt.contains("photo")) {
            return "Ajoutez une photo de profil pour que vos voisins vous reconnaissent ! 📸";
        }
        if (prompt.contains("téléphone") || prompt.contains("gouvernorat")) {
            return "Complétez votre localisation pour que votre municipalité puisse mieux vous contacter ! 📍";
        }
        return "Chaque information ajoutée renforce votre impact dans la communauté CityVoice ! 💪";
    }

    @PostMapping("/screen-name")
    public ResponseEntity<?> screenName(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.ok(Map.of("appropriate", true));
        }

        var result = nameModerationService.screen(name);

        return ResponseEntity.ok(Map.of(
                "appropriate", result.appropriate(),
                "reason", result.reason() != null ? result.reason() : ""
        ));
    }

    @PostMapping("/generate-bio")
    public ResponseEntity<?> generateBio(@RequestBody Map<String, Object> body) {
        String nom         = (String) body.get("nom");
        String ville       = (String) body.get("ville");
        String gouvernorat = (String) body.get("gouvernorat");
        String role        = (String) body.get("role");
        int    points      = body.get("points") != null
                ? ((Number) body.get("points")).intValue() : 0;
        int    badges      = body.get("badges") != null
                ? ((Number) body.get("badges")).intValue() : 0;
        int    streak      = body.get("streak") != null
                ? ((Number) body.get("streak")).intValue() : 0;
        String since       = (String) body.getOrDefault("since", "");

        String roleLabel = switch (role != null ? role : "") {
            case "CITOYEN"       -> "citoyen";
            case "CHEF_EQUIPE"   -> "chef d'équipe municipal";
            case "MEMBRE_EQUIPE" -> "agent terrain municipal";
            case "MODERATEUR"    -> "modérateur";
            default              -> "membre";
        };

        String prompt = """
        Tu es un rédacteur pour CityVoice, une plateforme civique tunisienne.
        Génère une courte biographie publique (2-3 phrases max) pour ce profil :
        
        Nom: %s
        Rôle: %s
        Localisation: %s%s
        Points: %d
        Badges: %d
        Streak: %d jours consécutifs
        Membre depuis: %s
        
        La bio doit être:
        - Chaleureuse et positive
        - En français
        - Mentionner sa contribution à sa communauté
        - Inclure 1-2 emojis naturellement
        - Max 2 phrases
        - Ne pas commencer par le nom
        
        Réponds UNIQUEMENT avec la biographie, sans guillemets ni préambule.
        """.formatted(nom, roleLabel,
                ville != null ? ville : "",
                gouvernorat != null ? ", " + gouvernorat : "",
                points, badges, streak, since);

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = Map.of(
                    "model",       "llama-3.1-8b-instant",
                    "max_tokens",  150,
                    "temperature", 0.85,
                    "messages",    List.of(Map.of("role", "user", "content", prompt))
            );

            ResponseEntity<Map> response = new RestTemplate().exchange(
                    apiUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), Map.class
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");

            String bio = choices != null && !choices.isEmpty()
                    ? (String) ((Map<String, Object>) choices.get(0).get("message")).get("content")
                    : null;

            return ResponseEntity.ok(Map.of("bio", bio != null ? bio.trim() : ""));

        } catch (Exception e) {
            System.out.println("Bio gen error: " + e.getMessage());
            return ResponseEntity.ok(Map.of("bio", ""));
        }
    }
}