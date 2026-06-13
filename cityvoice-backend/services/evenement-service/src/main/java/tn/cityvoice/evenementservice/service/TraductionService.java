package tn.cityvoice.evenementservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tn.cityvoice.evenementservice.dto.request.TraductionRequest;
import tn.cityvoice.evenementservice.dto.response.TraductionResponse;
import java.util.*;

@Service
@Slf4j
public class TraductionService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.model}")
    private String groqModel;

    @Value("${mymemory.api.url}")
    private String myMemoryUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ═══════════════════════════════════════════════════
    // POINT D'ENTRÉE
    // ═══════════════════════════════════════════════════

    public TraductionResponse traduire(TraductionRequest req) {
        try {
            String texteTraduire;

            if ("arabe".equalsIgnoreCase(req.getLangue())) {
                texteTraduire = traduireAvecMyMemory(req.getTexte(), "fr", "ar");
            } else if ("anglais".equalsIgnoreCase(req.getLangue())) {
                texteTraduire = traduireAvecMyMemory(req.getTexte(), "fr", "en");
            } else if ("tunisien".equalsIgnoreCase(req.getLangue())) {
                texteTraduire = traduireAvecGroq(req.getTexte(), "tunisien");
            } else {
                texteTraduire = req.getTexte();
            }

            log.info("✅ Traduction {} réussie", req.getLangue());

            return TraductionResponse.builder()
                    .texteOriginal(req.getTexte())
                    .texteTraduire(texteTraduire)
                    .langue(req.getLangue())
                    .succes(true)
                    .build();

        } catch (Exception e) {
            log.error("❌ Erreur traduction : {}", e.getMessage());
            return TraductionResponse.builder()
                    .texteOriginal(req.getTexte())
                    .texteTraduire(req.getTexte())
                    .langue(req.getLangue())
                    .succes(false)
                    .build();
        }
    }

    // ═══════════════════════════════════════════════════
    // MY MEMORY — avec découpage en chunks
    // ═══════════════════════════════════════════════════

    private String traduireAvecMyMemory(String texte, String from, String to) {
        try {
            // Texte court → traduction directe
            if (texte.length() <= 500) {
                return traduireChunkMyMemory(texte, from, to);
            }

            // Texte long → découper en phrases et traduire par chunks
            String[] phrases = texte.split("(?<=[.!?])\\s+");
            StringBuilder chunk   = new StringBuilder();
            StringBuilder resultat = new StringBuilder();

            for (String phrase : phrases) {
                if (chunk.length() + phrase.length() > 450) {
                    if (chunk.length() > 0) {
                        String traduit = traduireChunkMyMemory(
                                chunk.toString().trim(), from, to
                        );
                        resultat.append(traduit).append(" ");
                        chunk = new StringBuilder();
                    }
                }
                chunk.append(phrase).append(" ");
            }

            // Dernier chunk restant
            if (chunk.length() > 0) {
                String traduit = traduireChunkMyMemory(
                        chunk.toString().trim(), from, to
                );
                resultat.append(traduit);
            }

            return resultat.toString().trim();

        } catch (Exception e) {
            log.error("❌ MyMemory erreur: {}", e.getMessage());
            // Fallback Groq
            return traduireAvecGroq(
                    texte, "ar".equals(to) ? "arabe" : "anglais"
            );
        }
    }

    private String traduireChunkMyMemory(String chunk, String from, String to) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(myMemoryUrl)
                    .queryParam("q", chunk)
                    .queryParam("langpair", from + "|" + to)
                    .queryParam("de", "cityvoice@gmail.com")
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map body = response.getBody();

            if (body != null) {
                Integer responseStatus = (Integer) body.get("responseStatus");
                if (responseStatus != null && responseStatus == 200) {
                    String traduit = extractMyMemoryResponse(body);
                    if (traduit != null && !traduit.isEmpty()
                            && !traduit.contains("INVALID")) {
                        return traduit;
                    }
                }
                log.warn("⚠️ MyMemory status: {} → fallback Groq", responseStatus);
            }

            // Fallback Groq pour ce chunk
            return traduireChunkGroq(
                    chunk, "ar".equals(to) ? "arabe" : "anglais"
            );

        } catch (Exception e) {
            log.error("❌ Chunk MyMemory erreur: {}", e.getMessage());
            return traduireChunkGroq(
                    chunk, "ar".equals(to) ? "arabe" : "anglais"
            );
        }
    }

    // ═══════════════════════════════════════════════════
    // GROQ — avec découpage en chunks si texte long
    // ═══════════════════════════════════════════════════

    private String traduireAvecGroq(String texte, String langue) {
        try {
            // Texte trop long → découper
            if (texte.length() > 2000) {
                String[] phrases = texte.split("(?<=[.!?])\\s+");
                StringBuilder chunk    = new StringBuilder();
                StringBuilder resultat = new StringBuilder();

                for (String phrase : phrases) {
                    if (chunk.length() + phrase.length() > 1500) {
                        if (chunk.length() > 0) {
                            String traduit = traduireChunkGroq(
                                    chunk.toString().trim(), langue
                            );
                            resultat.append(traduit).append(" ");
                            chunk = new StringBuilder();
                        }
                    }
                    chunk.append(phrase).append(" ");
                }
                if (chunk.length() > 0) {
                    resultat.append(
                            traduireChunkGroq(chunk.toString().trim(), langue)
                    );
                }
                return resultat.toString().trim();
            }

            // Texte court → traduction directe
            return traduireChunkGroq(texte, langue);

        } catch (Exception e) {
            log.error("❌ Groq erreur: {}", e.getMessage());
            return texte;
        }
    }

    private String traduireChunkGroq(String texte, String langue) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", groqModel);
        body.put("max_tokens", 2048);
        body.put("temperature", 0.1);

        String systemContent;
        String userContent;

        if ("arabe".equalsIgnoreCase(langue)) {
            systemContent =
                    "Tu es un traducteur français-arabe professionnel. " +
                            "Traduis le texte en arabe standard moderne (فصحى). " +
                            "Retourne UNIQUEMENT la traduction, sans explication ni commentaire.";
            userContent = "Traduis en arabe : " + texte;

        } else if ("anglais".equalsIgnoreCase(langue)) {
            systemContent =
                    "You are a professional French to English translator. " +
                            "Return ONLY the English translation, nothing else. " +
                            "No explanations, no comments.";
            userContent = "Translate to English: " + texte;

        } else {
            // Tunisien
            systemContent =
                    "You are a Tunisian dialect translator. " +
                            "Write ONLY in Arabic script. " +
                            "Use authentic Tunisian Darija words like: " +
                            "مليح، برشا، يزي، هكا، باهي، انجموا، معانا، نهار";
            userContent = buildPromptTunisien(texte);
        }

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemContent);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        body.put("messages", List.of(systemMsg, userMsg));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + groqApiKey);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                groqApiUrl,
                new HttpEntity<>(body, headers),
                Map.class
        );

        return extractGroqResponse(response.getBody());
    }

    // ═══════════════════════════════════════════════════
    // PROMPTS
    // ═══════════════════════════════════════════════════

    private String buildPromptTunisien(String texte) {
        return """
            Translate to Tunisian Arabic dialect (Darija).
            Arabic script ONLY.
            
            Examples:
            "Bonjour" → "أهلا"
            "Rejoignez-nous" → "انجموا معانا"
            "journée de nettoyage" → "نهار تنظيف"
            "C'est bien" → "مليح"
            "Beaucoup" → "برشا"
            "Ensemble" → "مع بعضنا"
            
            Translate: %s
            
            Tunisian Arabic only:
            """.formatted(texte);
    }

    // ═══════════════════════════════════════════════════
    // EXTRACTEURS
    // ═══════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private String extractMyMemoryResponse(Map body) {
        if (body == null) return "";
        try {
            Map<String, Object> responseData =
                    (Map<String, Object>) body.get("responseData");
            return (String) responseData.get("translatedText");
        } catch (Exception e) {
            log.error("Erreur extraction MyMemory : {}", e.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractGroqResponse(Map body) {
        if (body == null) return "";
        try {
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) body.get("choices");
            Map<String, Object> message =
                    (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("Erreur extraction Groq : {}", e.getMessage());
            return "";
        }
    }
}