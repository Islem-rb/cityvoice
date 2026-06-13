package tn.cityvoice.evenementservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tn.cityvoice.evenementservice.dto.response.SuggestionAnalyseResponse;
import tn.cityvoice.evenementservice.entity.Suggestion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final Map<Long, SuggestionAnalyseResponse> cache = new ConcurrentHashMap<>();

    // Point d'entrée principal
    public SuggestionAnalyseResponse analyserSuggestion(Suggestion suggestion) {
        if (cache.containsKey(suggestion.getId())) {
            log.info("✅ Cache hit pour suggestion {}", suggestion.getId());
            return cache.get(suggestion.getId());
        }
        try {
            String prompt = buildPrompt(suggestion);
            String response = callGroq(prompt);
            SuggestionAnalyseResponse result = parseResponse(response);
            cache.put(suggestion.getId(), result);
            log.info("✅ Groq analyse réussie pour suggestion {}", suggestion.getId());
            return result;
        } catch (Exception e) {
            log.error("Erreur Groq : {}", e.getMessage());
            log.info("⚠️ Utilisation du fallback pour suggestion {}", suggestion.getId());
            return fallbackAnalyse(suggestion);
        }
    }

    // Construction du prompt
    private String buildPrompt(Suggestion suggestion) {
        return String.format(
                "Tu es un assistant municipal intelligent. " +
                        "Analyse cette suggestion d'événement citoyen et réponds UNIQUEMENT en JSON valide.\n\n" +
                        "Suggestion :\n" +
                        "- Titre : %s\n" +
                        "- Description : %s\n" +
                        "- Type souhaité : %s\n" +
                        "- Lieu souhaité : %s\n" +
                        "- Date souhaitée : %s\n\n" +
                        "Réponds UNIQUEMENT avec ce JSON (sans texte avant ou après) :\n" +
                        "{\n" +
                        "  \"scorePertinence\": <nombre entre 0 et 100>,\n" +
                        "  \"niveauImpact\": \"<FAIBLE ou MOYEN ou ÉLEVÉ>\",\n" +
                        "  \"estimationParticipation\": \"<ex: 50-100 personnes>\",\n" +
                        "  \"recommandation\": \"<CRÉER ou REJETER ou ATTENDRE>\",\n" +
                        "  \"justificationFr\": \"<explication en français, 2-3 phrases>\",\n" +
                        "  \"justificationEn\": \"<explanation in English, 2-3 sentences>\",\n" +
                        "  \"categorieImpact\": \"<Éducatif ou Environnemental ou Culturel ou Social ou Économique>\"\n" +
                        "}",
                suggestion.getTitre(),
                suggestion.getDescription()   != null ? suggestion.getDescription()   : "Non précisée",
                suggestion.getTypeSouhaite()  != null ? suggestion.getTypeSouhaite()  : "Non précisé",
                suggestion.getLieuSouhaite()  != null ? suggestion.getLieuSouhaite()  : "Non précisé",
                suggestion.getDateSouhaitee() != null ? suggestion.getDateSouhaitee() : "Non précisée"
        );
    }

    // Appel API Groq
    private String callGroq(String prompt) {
        String requestBody = String.format("""
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "Tu es un assistant municipal. Tu réponds UNIQUEMENT en JSON valide, sans markdown, sans texte supplémentaire."
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": 0.3,
                  "max_tokens": 500
                }
                """,
                model,
                prompt.replace("\"", "\\\"").replace("\n", "\\n")
        );

        return webClientBuilder.build()
                .post()
                .uri(apiUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    // Parsing de la réponse Groq
    private SuggestionAnalyseResponse parseResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);

        // Format OpenAI : choices[0].message.content
        String text = root
                .path("choices").get(0)
                .path("message")
                .path("content").asText();

        // Nettoyer les backticks markdown si présents
        text = text.replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        JsonNode json = objectMapper.readTree(text);

        return SuggestionAnalyseResponse.builder()
                .scorePertinence(json.path("scorePertinence").asInt(50))
                .niveauImpact(json.path("niveauImpact").asText("MOYEN"))
                .estimationParticipation(json.path("estimationParticipation").asText("30-50 personnes"))
                .recommandation(json.path("recommandation").asText("ATTENDRE"))
                .justificationFr(json.path("justificationFr").asText(""))
                .justificationEn(json.path("justificationEn").asText(""))
                .categorieImpact(json.path("categorieImpact").asText("Social"))
                .build();
    }

    // Fallback intelligent
    private SuggestionAnalyseResponse fallbackAnalyse(Suggestion suggestion) {
        int score = 20;
        String categorie     = "Social";
        String participation = "20-50 personnes";

        String titre = suggestion.getTitre() != null ?
                suggestion.getTitre().toLowerCase() : "";
        String desc  = suggestion.getDescription() != null ?
                suggestion.getDescription().toLowerCase() : "";
        String tout  = titre + " " + desc;

        if (tout.contains("education") || tout.contains("formation") ||
                tout.contains("atelier") || tout.contains("apprentissage")) {
            score += 20; categorie = "Éducatif"; participation = "30-80 personnes";
        }
        if (tout.contains("recyclage") || tout.contains("environnement") ||
                tout.contains("ecologie") || tout.contains("vert")) {
            score += 20; categorie = "Environnemental"; participation = "40-100 personnes";
        }
        if (tout.contains("culture") || tout.contains("art") ||
                tout.contains("musique") || tout.contains("theatre")) {
            score += 15; categorie = "Culturel"; participation = "50-150 personnes";
        }
        if (tout.contains("sport") || tout.contains("sante") ||
                tout.contains("bien-etre")) {
            score += 15; categorie = "Social"; participation = "30-70 personnes";
        }
        if (tout.contains("seminaire") || tout.contains("conference") ||
                tout.contains("forum")) {
            score += 10; categorie = "Éducatif"; participation = "50-200 personnes";
        }

        if (suggestion.getDescription() != null &&
                suggestion.getDescription().length() > 150) {
            score += 15;
        } else if (suggestion.getDescription() != null &&
                suggestion.getDescription().length() > 50) {
            score += 8;
        }

        if (suggestion.getDateSouhaitee() != null)              score += 10;
        if (suggestion.getLieuSouhaite()  != null &&
                !suggestion.getLieuSouhaite().isEmpty())         score += 10;

        if (suggestion.getTypeSouhaite() != null) {
            score += 5;
            switch (suggestion.getTypeSouhaite().name()) {
                case "EDUCATION" -> { categorie = "Éducatif";       participation = "40-100 personnes"; }
                case "RECYCLAGE" -> { categorie = "Environnemental"; participation = "50-120 personnes"; }
                case "SEMINAIRE" -> { categorie = "Éducatif";       participation = "60-200 personnes"; }
                case "BENEVOLE"  -> { categorie = "Social";         participation = "20-60 personnes";  }
                case "PAYANT"    -> { categorie = "Culturel";       participation = "30-100 personnes"; }
            }
        }

        score = Math.min(score, 95);

        String impact;
        String recommandation;

        if      (score >= 75) { impact = "ÉLEVÉ";  recommandation = "CRÉER";    }
        else if (score >= 55) { impact = "MOYEN";  recommandation = "CRÉER";    }
        else if (score >= 45) { impact = "MOYEN";  recommandation = "ATTENDRE"; }
        else                  { impact = "FAIBLE"; recommandation = "REJETER";  }

        return SuggestionAnalyseResponse.builder()
                .scorePertinence(score)
                .niveauImpact(impact)
                .estimationParticipation(participation)
                .recommandation(recommandation)
                .justificationFr(buildJustificationFr(categorie, suggestion, recommandation))
                .justificationEn(buildJustificationEn(categorie, suggestion, recommandation))
                .categorieImpact(categorie)
                .build();
    }

    //Justification Français
    private String buildJustificationFr(String categorie,
                                        Suggestion s, String recommandation) {
        StringBuilder sb = new StringBuilder();
        switch (recommandation) {
            case "CRÉER"    -> sb.append("Cette suggestion présente un fort potentiel pour la communauté. ");
            case "ATTENDRE" -> sb.append("Cette suggestion mérite attention mais nécessite plus de détails. ");
            default         -> sb.append("Cette suggestion manque d'éléments suffisants pour être retenue. ");
        }
        sb.append("De catégorie ").append(categorie).append(", ");
        if (s.getLieuSouhaite() != null && !s.getLieuSouhaite().isEmpty()) {
            sb.append("le lieu proposé (").append(s.getLieuSouhaite()).append(") est un atout. ");
        } else {
            sb.append("un lieu précis renforcerait la proposition. ");
        }
        if (s.getDateSouhaitee() != null) {
            sb.append("La date souhaitée facilite la planification.");
        } else {
            sb.append("Une date souhaitée aiderait à la planification.");
        }
        return sb.toString();
    }

    // Justification Anglais
    private String buildJustificationEn(String categorie,
                                        Suggestion s, String recommandation) {
        StringBuilder sb = new StringBuilder();
        switch (recommandation) {
            case "CRÉER"    -> sb.append("This suggestion shows strong potential for the community. ");
            case "ATTENDRE" -> sb.append("This suggestion deserves attention but needs more details. ");
            default         -> sb.append("This suggestion lacks sufficient elements to be retained. ");
        }
        sb.append("Categorized as ").append(categorie).append(", ");
        if (s.getLieuSouhaite() != null && !s.getLieuSouhaite().isEmpty()) {
            sb.append("the proposed venue (").append(s.getLieuSouhaite()).append(") is an asset. ");
        } else {
            sb.append("a specific venue would strengthen the proposal. ");
        }
        if (s.getDateSouhaitee() != null) {
            sb.append("The suggested date facilitates planning.");
        } else {
            sb.append("A suggested date would help with planning.");
        }
        return sb.toString();
    }
}