package tn.cityvoice.actualiteservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BadWordsService — filtre les insultes et grossièretés côté backend.
 *
 * Architecture à 3 couches (exécutées dans cet ordre) :
 *   1) Patterns STRICTS sur la version normalisée 1:1 (diacritiques retirés,
 *      leet substitué, minuscules). Détecte les variantes simples sans faux
 *      positifs (limites de mot).
 *   2) Patterns FLOUS tolérant la répétition de caractères (ex : "fuuuck",
 *      "m3rrrde") sur la version "lettres uniquement" avec remappage des
 *      indices vers le texte original via un tableau de positions.
 *   3) API Ninjas /profanityfilter — 2ᵉ couche de sécurité (réseau).
 *      Si l'API est indisponible ou renvoie une sortie de taille différente,
 *      on retombe automatiquement sur le résultat local (fallback).
 *
 * Chaque occurrence détectée est remplacée par '*' de même longueur
 * dans le texte original, pour préserver les espaces et la ponctuation.
 *
 * L'API publique (hasBadWords / filter) reste identique à la version
 * précédente — aucun appelant n'a besoin d'être modifié.
 */
@Service
public class BadWordsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadWordsService.class);

    // ─── Liste de mots interdits ────────────────────────────────────────────
    private static final List<String> BAD_WORDS = Arrays.asList(
            // FRANÇAIS
            "merde", "putain", "connard", "connasse", "salope", "pute",
            "enculé", "encule", "enculer", "batard", "bâtard", "fdp",
            "niquer", "nique", "ta gueule", "tg", "abruti", "abrutie",
            "imbecile", "imbécile", "cretin", "crétin", "debile", "débile",
            "ordure", "salopard", "cochon", "baise", "baiser",
            "branleur", "branleuse", "branler", "chier", "chieur",
            "emmerde", "emmerder", "emmerdeur", "foutre", "foutaise",
            "bite", "couille", "couilles",

            // ANGLAIS
            "fuck", "fucking", "fucker", "fucked", "motherfucker",
            "shit", "bullshit", "asshole", "bitch", "bastard", "cunt",
            "dick", "cock", "pussy", "whore", "slut", "moron",
            "kys", "stfu",

            // ARABE / DIALECTE TUNISIEN (translittéré latin)
            "kess", "kes", "kis", "zob", "zebi", "zbbi", "7mar", "hmar",
            "kalb", "kelbek", "sharmouta", "charmota", "zamel", "zemel",
            "omek", "boytek", "boitek", "ahbal", "manyak",
            "nhik", "kahba", "gahba", "khawel", "zbel", "tiz"
    );

    // ─── Substitutions leet speak ──────────────────────────────────────────
    // Appliquées pendant la normalisation : "f4ck" → "fack", "sh1t" → "shit".
    private static final Map<Character, Character> LEET_MAP = Map.ofEntries(
            Map.entry('0', 'o'),
            Map.entry('1', 'i'),
            Map.entry('3', 'e'),
            Map.entry('4', 'a'),
            Map.entry('5', 's'),
            Map.entry('7', 't'),
            Map.entry('8', 'b'),
            Map.entry('9', 'g'),
            Map.entry('@', 'a'),
            Map.entry('$', 's'),
            Map.entry('!', 'i')
    );

    // ─── API Ninjas ─────────────────────────────────────────────────────────
    private static final String API_NINJAS_URL = "https://api.api-ninjas.com/v1/profanityfilter";

    @Value("${apininjas.api.key:}")
    private String apiNinjasKey;

    private final RestTemplate restTemplate;

    /** Patterns stricts — appliqués sur la version normalisée 1:1. */
    private final List<Pattern> strictPatterns;

    /** Patterns flous — tolèrent la répétition de caractères (ex: "fuuuck"). */
    private final List<Pattern> fuzzyPatterns;

    public BadWordsService() {
        // RestTemplate avec timeouts courts pour ne pas ralentir les créations
        // de posts/commentaires si l'API est lente ou indisponible.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);

        // Patterns stricts : même logique que l'ancienne version, appliquée
        // ensuite sur le texte NORMALISÉ (donc résiste à : MERDE, mérde, m3rde).
        this.strictPatterns = BAD_WORDS.stream()
                .map(word -> {
                    String norm = normalizeWord(word);
                    return Pattern.compile(
                            "(?<![a-zA-Z])" + Pattern.quote(norm) + "(?![a-zA-Z])",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                    );
                })
                .toList();

        // Patterns flous : chaque caractère peut être répété 1..N fois.
        // Appliqués sur la version "lettres uniquement" (sans espaces ni
        // ponctuation) pour attraper "m e r d e", "f.u.c.k", "fuuuck", etc.
        this.fuzzyPatterns = BAD_WORDS.stream()
                .map(this::buildFuzzyPattern)
                .filter(p -> p != null)
                .toList();
    }

    /** Construit un pattern flou "f+u+c+k+" à partir d'un mot. */
    private Pattern buildFuzzyPattern(String word) {
        String letters = normalizeWord(word).replaceAll("[^a-z]", "");
        if (letters.length() < 3) {
            // On évite les mots de 1-2 lettres en fuzzy (trop de faux positifs).
            return null;
        }
        // Les caractères sont déjà garantis dans [a-z] (aucun méta-caractère regex
        // possible), donc on peut les concaténer directement sans Pattern.quote().
        StringBuilder sb = new StringBuilder();
        for (char c : letters.toCharArray()) {
            sb.append(c).append('+');
        }
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Minuscules + diacritiques retirés (pour un mot de la liste). */
    private static String normalizeWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // ─── Version normalisée d'un texte utilisateur ─────────────────────────

    /**
     * Résultat de la normalisation d'un texte utilisateur.
     * - {@code sameLength} : même longueur que l'original — diacritiques retirés,
     *   leet substitué, minuscules. Les indices correspondent 1:1 à l'original.
     * - {@code stripped} : uniquement les lettres a-z (pour les patterns flous).
     * - {@code positions} : pour chaque index de {@code stripped}, l'index
     *   correspondant dans le texte original.
     */
    private static final class Normalized {
        final String sameLength;
        final String stripped;
        final int[] positions;

        Normalized(String sameLength, String stripped, int[] positions) {
            this.sameLength = sameLength;
            this.stripped = stripped;
            this.positions = positions;
        }
    }

    private static Normalized normalize(String text) {
        StringBuilder sameLen = new StringBuilder(text.length());
        StringBuilder stripped = new StringBuilder(text.length());
        int[] positions = new int[text.length()];
        int strippedIdx = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));

            // 1) Retirer les diacritiques caractère par caractère pour
            //    préserver la longueur 1:1 avec l'original.
            char base = c;
            String decomp = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            for (int k = 0; k < decomp.length(); k++) {
                char dc = decomp.charAt(k);
                int type = Character.getType(dc);
                if (type != Character.NON_SPACING_MARK
                        && type != Character.ENCLOSING_MARK
                        && type != Character.COMBINING_SPACING_MARK) {
                    base = dc;
                    break;
                }
            }

            // 2) Substitution leet (0→o, 1→i, 3→e, @→a, etc.)
            Character leet = LEET_MAP.get(base);
            if (leet != null) base = leet;

            sameLen.append(base);

            // 3) Extraction lettres uniquement pour la passe fuzzy
            if (base >= 'a' && base <= 'z') {
                positions[strippedIdx] = i;
                stripped.append(base);
                strippedIdx++;
            }
        }

        int[] truncated = Arrays.copyOf(positions, strippedIdx);
        return new Normalized(sameLen.toString(), stripped.toString(), truncated);
    }

    // ─── API publique ──────────────────────────────────────────────────────

    /**
     * Retourne true si le texte contient au moins un mot interdit
     * (locale stricte, locale floue, ou détection par API Ninjas).
     */
    public boolean hasBadWords(String text) {
        if (text == null || text.isBlank()) return false;

        Normalized norm = normalize(text);

        for (Pattern p : strictPatterns) {
            if (p.matcher(norm.sameLength).find()) return true;
        }
        for (Pattern p : fuzzyPatterns) {
            if (p.matcher(norm.stripped).find()) return true;
        }

        // API Ninjas — si disponible. Si l'API renvoie un texte différent,
        // c'est qu'elle a détecté quelque chose.
        String censored = callApiNinjas(text);
        return censored != null && !censored.equals(text);
    }

    /**
     * Remplace chaque occurrence de mot interdit par '*' de même longueur
     * dans le texte original. Applique les 3 couches (strict, flou, API Ninjas).
     * Si l'API est indisponible, fallback automatique sur le résultat local.
     */
    public String filter(String text) {
        if (text == null || text.isBlank()) return text;

        Normalized norm = normalize(text);
        char[] out = text.toCharArray();

        // 1) Phase STRICTE — les indices sont 1:1 avec l'original.
        for (Pattern p : strictPatterns) {
            Matcher m = p.matcher(norm.sameLength);
            while (m.find()) {
                for (int i = m.start(); i < m.end() && i < out.length; i++) {
                    out[i] = '*';
                }
            }
        }

        // 2) Phase FLOUE — les indices sont sur la version "lettres uniquement".
        //    On les remappe vers l'original via positions[] et on masque aussi
        //    les caractères intermédiaires (espaces, ponctuation) pour couvrir
        //    entièrement le mot obfusqué.
        for (Pattern p : fuzzyPatterns) {
            Matcher m = p.matcher(norm.stripped);
            while (m.find()) {
                int s = m.start();
                int e = m.end() - 1; // inclusif
                if (s >= norm.positions.length || e < 0 || e >= norm.positions.length || s > e) continue;
                int origStart = norm.positions[s];
                int origEnd = norm.positions[e];
                for (int i = origStart; i <= origEnd && i < out.length; i++) {
                    out[i] = '*';
                }
            }
        }

        String localResult = new String(out);

        // 3) API Ninjas — 2ᵉ couche de sécurité.
        //    On ne fusionne que si la longueur est identique (API Ninjas
        //    remplace les bad words par '*' de même taille, donc normalement OK).
        //    Sinon on garde juste le résultat local (fallback).
        String apiResult = callApiNinjas(text);
        if (apiResult != null && apiResult.length() == text.length()) {
            char[] merged = localResult.toCharArray();
            for (int i = 0; i < merged.length; i++) {
                if (apiResult.charAt(i) == '*') merged[i] = '*';
            }
            return new String(merged);
        }

        return localResult;
    }

    // ─── API Ninjas — appel HTTP ───────────────────────────────────────────

    /**
     * Appelle l'API Ninjas /profanityfilter et retourne le champ "censored".
     * Retourne null si la clé n'est pas configurée ou en cas d'erreur réseau
     * (fallback sur la liste locale côté appelant).
     */
    private String callApiNinjas(String text) {
        if (apiNinjasKey == null || apiNinjasKey.isBlank()) return null;
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(API_NINJAS_URL)
                    .queryParam("text", text)
                    .encode()
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", apiNinjasKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> resp = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, Map.class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) return null;

            Object censored = resp.getBody().get("censored");
            if (censored instanceof String) return (String) censored;
            return null;
        } catch (Exception e) {
            LOGGER.warn("[BadWordsService] API Ninjas indisponible, fallback local : {}", e.getMessage());
            return null;
        }
    }
}
