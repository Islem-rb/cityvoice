package tn.cityvoice.personnelservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;

@Service
public class IAEmbedding {

    private static final Logger log = LoggerFactory.getLogger(IAEmbedding.class);

    private final RestTemplate restTemplate;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    // ── Modèle d'embedding ────────────────────────────────────────────────────
    // nomic-embed-text : multilingue, robuste, pas de NaN avec le français
    // Pour l'installer : ollama pull nomic-embed-text
    // Si vous voulez garder bge-m3 : changer en "bge-m3"
    @Value("${ollama.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    public IAEmbedding(@Qualifier("ollamaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Génère un vecteur d'embedding via Ollama.
     * Le texte est nettoyé en ASCII avant envoi pour éviter tout NaN.
     */
    public float[] embed(String text) {

        // Nettoyage ASCII garanti — dernier rempart avant envoi à Ollama
        String safeText = toAsciiSafe(text);

        if (safeText.isBlank()) {
            throw new RuntimeException("Texte vide apres nettoyage, impossible d'embedder");
        }

        log.info("Embedding ({}) : {} chars", embeddingModel, safeText.length());
        log.debug("Debut texte : [{}]",
                safeText.length() > 150 ? safeText.substring(0, 150) + "..." : safeText);

        Map<String, Object> request = Map.of(
                "model",  embeddingModel,
                "prompt", safeText
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                ollamaBaseUrl + "/api/embeddings",
                request,
                Map.class
        );

        if (response == null || response.get("embedding") == null) {
            throw new RuntimeException("Reponse embedding vide depuis Ollama");
        }

        List<?> rawList = (List<?>) response.get("embedding");

        float[] vector = new float[rawList.size()];
        for (int i = 0; i < rawList.size(); i++) {
            Number n = (Number) rawList.get(i);
            float v = n.floatValue();
            // Si NaN encore présent malgré le nettoyage → remplacer par 0
            vector[i] = Float.isNaN(v) || Float.isInfinite(v) ? 0f : v;
        }

        return vector;
    }

    /**
     * Similarité cosinus entre deux vecteurs.
     */
    public double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            throw new IllegalArgumentException("Vecteurs incompatibles ou vides");
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    // ── Nettoyage ASCII ───────────────────────────────────────────────────────

    /**
     * Convertit tout texte en ASCII pur (32-126) + sauts de ligne.
     *
     * Étapes :
     *  1. NFD : décompose les accents (é → e + combining)
     *  2. Supprime les diacritiques (combining marks)
     *  3. Remplace caractères spéciaux courants par ASCII
     *  4. Supprime tout ce qui reste hors 32-126 (PUA, symboles, etc.)
     */
    private String toAsciiSafe(String input) {
        if (input == null) return "";

        // Étape 1+2 : NFD + supprimer diacritiques
        String s = Normalizer.normalize(input, Normalizer.Form.NFD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}", "");

        // Étape 3 : remplacements sémantiques
        s = s
                .replace("\u2013", "-").replace("\u2014", "-")   // tirets longs
                .replace("\u2019", "'").replace("\u2018", "'")   // apostrophes
                .replace("\u201C", "\"").replace("\u201D", "\"") // guillemets
                .replace("\u00AB", "\"").replace("\u00BB", "\"") // « »
                .replace("\u2022", "-").replace("\u25A0", "-")   // puces
                .replace("\u25CF", "-").replace("\u25AA", "-")   // puces
                .replace("\u00A0", " ").replace("\u202F", " ")   // espaces spéciaux
                .replace("\uFB01", "fi").replace("\uFB02", "fl") // ligatures
                .replace("\u2026", "...");                        // ellipses

        // Étape 4 : filtre ASCII strict
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if ((c >= 32 && c <= 126) || c == '\n' || c == '\t') {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }

        return sb.toString()
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }
}