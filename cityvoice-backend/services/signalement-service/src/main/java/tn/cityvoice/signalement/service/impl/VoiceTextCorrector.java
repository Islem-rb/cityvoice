package tn.cityvoice.signalement.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Correcteur fuzzy des transcriptions vocales.
 *
 * Whisper commet souvent des quasi-erreurs sur des mots peu fréquents :
 *   - "nid de poulet"       → "nid-de-poule"
 *   - "la Marta"            → "La Marsa"
 *   - "El Menza"            → "El Menzah"
 *   - "poubelle débordante" → "poubelle qui déborde"
 *
 * Ce composant applique une correction par distance de Levenshtein contre un
 * dictionnaire de référence (types d'infrastructures + toponymes tunisiens).
 *
 * Seuils :
 *   - distance ≤ 2 sur mots de longueur ≤ 8       → correction certaine
 *   - distance ≤ 3 sur mots de longueur 9-14      → correction probable
 *   - au-delà                                      → ne touche pas
 */
@Component
@Slf4j
public class VoiceTextCorrector {

    /** Expressions de problèmes — la forme canonique est la plus descriptive. */
    private static final List<String> PROBLEM_TERMS = List.of(
        "nid-de-poule",
        "trou dans la chaussée",
        "lampadaire cassé",
        "lampadaire en panne",
        "éclairage public",
        "fuite d'eau",
        "canalisation cassée",
        "déchets non collectés",
        "poubelle qui déborde",
        "ordures",
        "poteau endommagé",
        "poteau cassé",
        "signalisation manquante",
        "panneau de signalisation",
        "feu tricolore",
        "caniveau bouché",
        "eaux usées",
        "espace vert dégradé",
        "arbre tombé",
        "graffiti"
    );

    /** Toponymes tunisiens fréquents — 24 gouvernorats + quartiers du Grand Tunis. */
    private static final List<String> PLACE_TERMS = List.of(
        // Gouvernorats
        "Tunis", "Ariana", "Ben Arous", "Manouba", "Nabeul", "Zaghouan",
        "Bizerte", "Béja", "Jendouba", "Kef", "Siliana", "Kairouan",
        "Kasserine", "Sidi Bouzid", "Sousse", "Monastir", "Mahdia",
        "Sfax", "Gafsa", "Tozeur", "Kebili", "Gabès", "Medenine", "Tataouine",
        // Quartiers Grand Tunis
        "El Menzah", "Ennasr", "Hay Ennasr", "La Marsa", "Gammarth",
        "Carthage", "Le Bardo", "Lafayette", "Berges du Lac", "Belvédère",
        "Mutuelleville", "Le Kram", "Mornag", "Ezzahra", "Hammam Lif",
        "La Goulette", "El Manar", "Cité Olympique", "Montplaisir",
        "Bab Souika", "Medina", "Raoued", "Soukra", "Charguia", "Aouina",
        "El Omrane", "Ibn Khaldoun", "Cité Ghazala",
        // Repères de proximité (utilisés pour localiser : "à côté de la mosquée", "près du lycée")
        "mosquée", "école", "lycée", "collège", "hôpital", "pharmacie",
        "boulangerie", "supermarché", "station", "gare", "stade",
        "marché", "parc", "jardin", "banque", "poste"
    );

    /**
     * Corrections phonétiques ciblées — patterns regex appliqués AVANT le fuzzy-match.
     * Couvre les erreurs récurrentes de Whisper qu'un Levenshtein ne rattrape pas
     * (distance > 3 entre forme erronée et forme correcte).
     *
     * Format : { regex, remplacement }
     *   - regex : case-insensitive via (?i) en préfixe
     *   - remplacement : peut contenir $1, $2... pour les groupes capturés
     */
    private static final String[][] PHONETIC_FIXES = {
        // "Routes dans la rue/chaussée" → "Un trou dans la rue/chaussée"
        // Whisper confond souvent "trou" avec "route(s)" quand suivi de "dans la..."
        {"(?i)\\broutes?\\s+dans\\s+(la|le)\\s+(rue|chauss[eé]e|route|avenue|boulevard)\\b",
         "un trou dans $1 $2"},

        // "du mosquet" / "de la mosquet" → "de la mosquée"
        {"(?i)\\b(du|de\\s+la)\\s+mosquet\\b", "de la mosquée"},
        // "au mosquet" → "à la mosquée"
        {"(?i)\\bau\\s+mosquet\\b", "à la mosquée"},
        // "la mosquet" / "le mosquet" → "la mosquée"
        {"(?i)\\bla\\s+mosquet\\b", "la mosquée"},
        {"(?i)\\ble\\s+mosquet\\b", "la mosquée"},

        // "nid de poulet" (Whisper mishear fréquent) → "nid-de-poule"
        {"(?i)\\bnid\\s+de\\s+poulets?\\b", "nid-de-poule"},

        // "lampe à daire" / "lampe aider" → "lampadaire"
        {"(?i)\\blampe\\s+[aà]\\s+daire\\b", "lampadaire"},
        {"(?i)\\blampe\\s+aider\\b", "lampadaire"},

        // "collectés" / "connectés" (proche phonétique) quand suivi de "déchets/ordures"
        {"(?i)\\bd[eé]chets?\\s+non\\s+connect[eé]s\\b", "déchets non collectés"},
        {"(?i)\\bordures?\\s+non\\s+connect[eé]es\\b",   "ordures non collectées"},

        // "fuite d'eau" parfois entendu "fuite de l'eau"
        {"(?i)\\bfuite\\s+de\\s+l['’]\\s*eau\\b", "fuite d'eau"},

        // "caniveau" souvent transcrit "kanivo", "canivos"
        {"(?i)\\bcaniv[oô]s?\\b",  "caniveau"},
        {"(?i)\\bkanivos?\\b",     "caniveau"},

        // "à côté du/de la" — normalisations
        {"(?i)\\bacot[eé]\\s+de\\b", "à côté de"},
    };

    /**
     * Corrige une transcription en 3 passes :
     *   1. Corrections phonétiques ciblées (regex patterns pour erreurs récurrentes)
     *   2. Fuzzy-match sur expressions multi-mots (types de problèmes)
     *   3. Fuzzy-match sur toponymes (mots/groupes isolés)
     */
    public String correct(String text) {
        if (text == null || text.isBlank()) return text;

        String corrected = text;

        // ── Passe 0 : corrections phonétiques regex (Whisper mishears récurrents)
        for (String[] pair : PHONETIC_FIXES) {
            corrected = corrected.replaceAll(pair[0], pair[1]);
        }

        // ── Passe 1 : expressions multi-mots (problèmes)
        for (String canonical : PROBLEM_TERMS) {
            corrected = fuzzyReplacePhrase(corrected, canonical);
        }

        // ── Passe 2 : toponymes et repères de proximité
        for (String canonical : PLACE_TERMS) {
            corrected = fuzzyReplaceWord(corrected, canonical);
        }

        if (!corrected.equals(text)) {
            log.info("[VOICE-FIX] '{}' → '{}'", text, corrected);
        }
        return corrected;
    }

    /**
     * Remplace les occurrences "presque identiques" d'une phrase canonique
     * en scannant des fenêtres glissantes de même nombre de mots.
     */
    private String fuzzyReplacePhrase(String input, String canonical) {
        String[] canonWords = canonical.split("\\s+");
        if (canonWords.length < 2) return input; // géré par fuzzyReplaceWord

        String[] inputWords = input.split("\\s+");
        if (inputWords.length < canonWords.length) return input;

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < inputWords.length) {
            // Extraire une fenêtre de même taille que la phrase canonique
            if (i + canonWords.length <= inputWords.length) {
                StringBuilder window = new StringBuilder();
                for (int k = 0; k < canonWords.length; k++) {
                    if (k > 0) window.append(" ");
                    window.append(inputWords[i + k]);
                }
                String candidate = window.toString();
                int threshold = maxEditDistance(canonical.length());
                if (!candidate.equalsIgnoreCase(canonical)
                    && levenshtein(normalize(candidate), normalize(canonical)) <= threshold) {
                    out.append(canonical);
                    i += canonWords.length;
                    if (i < inputWords.length) out.append(" ");
                    continue;
                }
            }
            out.append(inputWords[i]);
            if (i + 1 < inputWords.length) out.append(" ");
            i++;
        }
        return out.toString();
    }

    /**
     * Remplace un mot (toponyme) mal orthographié par sa forme canonique.
     * Ne touche pas aux mots courts (< 4 lettres) pour éviter les faux positifs.
     */
    private String fuzzyReplaceWord(String input, String canonical) {
        String[] canonParts = canonical.split("\\s+");

        // Cas simple : toponyme un seul mot (ex : "Tunis", "Ariana")
        if (canonParts.length == 1) {
            if (canonical.length() < 4) return input; // trop court pour fuzzy-match
            return replaceSingleWordFuzzy(input, canonical);
        }

        // Toponyme multi-mots (ex : "Le Bardo", "La Marsa") — déléguer à fuzzyReplacePhrase
        return fuzzyReplacePhrase(input, canonical);
    }

    private String replaceSingleWordFuzzy(String input, String canonical) {
        String[] words = input.split("\\s+");
        int threshold = maxEditDistance(canonical.length());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            // Strip trailing punctuation for comparison, then re-attach
            String punct = "";
            String w = words[i];
            while (!w.isEmpty() && !Character.isLetterOrDigit(w.charAt(w.length() - 1))) {
                punct = w.charAt(w.length() - 1) + punct;
                w = w.substring(0, w.length() - 1);
            }
            if (!w.equalsIgnoreCase(canonical)
                && w.length() >= canonical.length() - 2
                && w.length() <= canonical.length() + 2
                && levenshtein(normalize(w), normalize(canonical)) <= threshold) {
                out.append(canonical).append(punct);
            } else {
                out.append(words[i]);
            }
            if (i + 1 < words.length) out.append(" ");
        }
        return out.toString();
    }

    /** Seuil d'édition proportionnel à la longueur. */
    private static int maxEditDistance(int canonicalLen) {
        if (canonicalLen <= 5)  return 1;
        if (canonicalLen <= 8)  return 2;
        if (canonicalLen <= 14) return 3;
        return 4;
    }

    /** Normalise (minuscules + suppression des accents) pour comparaison. */
    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase(Locale.ROOT);
    }

    /** Distance de Levenshtein classique — O(n*m). */
    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
