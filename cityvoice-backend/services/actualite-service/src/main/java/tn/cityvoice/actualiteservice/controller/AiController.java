package tn.cityvoice.actualiteservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Endpoints IA pour les posts CityVoice.
 *
 * GET  /api/ai/generate-image?prompt=...
 *   → Recherche une vraie photo sur Pexels (gratuit, clé API requise)
 *   → Retourne l'URL de la photo en JSON (le browser Angular télécharge directement)
 *   → Évite le blocage Cloudflare côté serveur Java
 *
 * POST /api/ai/suggest-content
 *   → Génère un contenu via Pollinations.ai text API (gratuit, sans clé)
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:4200")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private static final String PEXELS_SEARCH_URL = "https://api.pexels.com/v1/search";
    private static final String POLLINATIONS_TEXT_URL = "https://text.pollinations.ai/";

    @Value("${pexels.api.key:}")
    private String pexelsApiKey;

    /** RestTemplate standard (texte, JSON) */
    private final RestTemplate restTemplate = new RestTemplate();

    // ─── DTOs ─────────────────────────────────────────────────────────

    public record SuggestRequest(String titre, String type) {}
    public record SuggestResponse(String content, boolean success, String error) {}
    public record PhotoResult(String url, String photographer, String photographerUrl) {}

    // ─── Endpoint image ───────────────────────────────────────────────

    /**
     * Retourne l'URL d'une vraie photo Pexels correspondant au prompt.
     * Le browser Angular télécharge directement l'image (bypass Cloudflare).
     * Requiert pexels.api.key dans application.properties.
     *
     * GET /api/ai/generate-image?prompt=trou+dans+la+route+tunis
     * → { "url": "https://images.pexels.com/...", "photographer": "John Doe", "photographerUrl": "..." }
     */
    @GetMapping("/generate-image")
    public ResponseEntity<?> generateImage(@RequestParam String prompt) {

        boolean keyConfigured = pexelsApiKey != null
                && !pexelsApiKey.isBlank()
                && !pexelsApiKey.equals("VOTRE_CLE_PEXELS_ICI");

        if (!keyConfigured) {
            log.warn("[AI Image] Clé Pexels non configurée — pexels.api.key dans application.properties");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Clé Pexels non configurée"));
        }

        try {
            PhotoResult result = searchPexelsPhoto(prompt);
            log.info("[AI Image] Photo Pexels trouvée pour '{}' : {}", prompt, result.url());
            return ResponseEntity.ok(result);

        } catch (NoResultException e) {
            log.warn("[AI Image] Aucun résultat Pexels pour : {}", prompt);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Aucune photo trouvée"));
        } catch (Exception e) {
            log.error("[AI Image] Erreur recherche Pexels : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** Cherche une photo sur Pexels et retourne le résultat avec URL + photographe */
    @SuppressWarnings("unchecked")
    private PhotoResult searchPexelsPhoto(String prompt) throws Exception {
        // Nettoyage du prompt
        String query = prompt.trim();
        if (query.length() > 100) query = query.substring(0, 100);

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String searchUrl = PEXELS_SEARCH_URL
                + "?query=" + encodedQuery
                + "&per_page=15"
                + "&orientation=landscape";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", pexelsApiKey);

        ResponseEntity<Map> response = restTemplate.exchange(
                searchUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        Map<String, Object> body = response.getBody();
        if (body == null) throw new RuntimeException("Réponse vide de Pexels");

        List<Map<String, Object>> photos = (List<Map<String, Object>>) body.get("photos");
        if (photos == null || photos.isEmpty()) throw new NoResultException();

        // Choisir une photo aléatoire parmi les résultats
        Map<String, Object> photo = photos.get(new Random().nextInt(photos.size()));
        Map<String, String> src = (Map<String, String>) photo.get("src");

        // "landscape" = 1200×627, taille idéale pour un post
        String url = src.get("landscape");
        if (url == null || url.isBlank()) url = src.get("large2x");
        if (url == null || url.isBlank()) url = src.get("large");

        String photographer = (String) photo.getOrDefault("photographer", "Pexels");
        String photographerUrl = (String) photo.getOrDefault("photographer_url", "https://www.pexels.com");

        return new PhotoResult(url, photographer, photographerUrl);
    }

    // ─── Endpoint suggestion contenu ─────────────────────────────────

    @PostMapping("/suggest-content")
    public ResponseEntity<SuggestResponse> suggestContent(@RequestBody SuggestRequest request) {
        if (request.titre() == null || request.titre().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new SuggestResponse(null, false, "Le titre ne peut pas être vide"));
        }
        try {
            String content = callPollinationsApi(request.titre(), request.type());
            return ResponseEntity.ok(new SuggestResponse(content, true, null));
        } catch (Exception e) {
            log.error("[AI] Erreur appel Pollinations text API", e);
            return ResponseEntity.ok(new SuggestResponse(null, false, "Erreur IA : " + e.getMessage()));
        }
    }

    // ─── Appel Pollinations text API ──────────────────────────────────

    private String callPollinationsApi(String titre, String type) {
        String typeLabel = switch (type != null ? type : "ACTUALITE") {
            case "EVENEMENT" -> "un événement";
            case "ANNONCE"   -> "une annonce";
            default          -> "une actualité";
        };

        String prompt =
            "Tu es un assistant de rédaction pour CityVoice, une application communautaire tunisienne. " +
            "L'utilisateur veut publier " + typeLabel + " avec le titre : \"" + titre + "\". " +
            "Rédige un contenu de publication en français (3 à 5 phrases), informatif et engageant, " +
            "adapté au contexte tunisien. Réponds UNIQUEMENT avec le texte du contenu, sans titre ni introduction.";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "CityVoice-Backend/1.0");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("model", "openai");
        body.put("seed", new Random().nextInt(9999));
        body.put("private", true);

        ResponseEntity<String> response = restTemplate.exchange(
                POLLINATIONS_TEXT_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        String text = response.getBody();
        if (text == null || text.isBlank()) throw new RuntimeException("Réponse vide");
        if (text.contains("IMPORTANT NOTICE") || text.contains("being deprecated")) {
            throw new RuntimeException("Message système Pollinations reçu au lieu du contenu");
        }
        return text.trim();
    }

    // ─── Exception interne ────────────────────────────────────────────

    private static class NoResultException extends RuntimeException {
        NoResultException() { super("Aucun résultat Pexels"); }
    }
}
