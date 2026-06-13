package tn.cityvoice.actualiteservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ContentModerationService — bloque la publication de contenus
 * politiques, haineux, religieusement sensibles, ou autrement
 * inappropriés sur la plateforme citoyenne CityVoice.
 *
 * Contrairement à {@link BadWordsService} qui se contente de filtrer
 * (remplacer les insultes par des '*'), ce service BLOQUE complètement
 * la publication si un sujet non autorisé est détecté. L'application
 * n'est pas une tribune politique et doit rester neutre.
 *
 * L'API publique expose :
 *  - {@link #checkContent(String...)}  : retourne un {@link ModerationResult}
 *    avec la liste des catégories déclenchées (ex : "politique", "religion").
 *  - {@link #isBlocked(String...)}     : raccourci boolean.
 *
 * Détection à deux couches :
 *   1) Liste de mots-clés par catégorie (FR + AR dialecte + EN).
 *   2) Patterns flous tolérant la répétition / diacritiques / leet,
 *      alignés sur la logique de BadWordsService.
 *
 * La liste n'est volontairement pas exhaustive : elle cible les
 * principaux noms de partis/dirigeants tunisiens et les slogans
 * religieux/haineux les plus courants. Elle peut être étendue via
 * application.properties si besoin (hook non branché pour l'instant).
 */
@Service
public class ContentModerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentModerationService.class);

    // ─── Catégorie POLITIQUE ────────────────────────────────────────────────
    // Partis, dirigeants, slogans, événements politiques tunisiens + généraux.
    private static final List<String> POLITICAL_KEYWORDS = Arrays.asList(
            // --- Partis & mouvements tunisiens ---
            "ennahdha", "ennahda", "nahdha", "nahda",
            "nidaa tounes", "nida tounes", "nidaa",
            "qalb tounes", "9alb tounes",
            "pdl", "parti destourien libre", "destourien",
            "tayyar",  "courant democrate",
            "echaab", "harakat echaab",
            "afek tounes",
            "ettakatol",
            "cpr", "congres pour la republique",
            "ettahrir", "hizb ettahrir",

            // --- Personnalités politiques tunisiennes ---
            "kais saied", "kais said", "kaies saied",
            "ghannouchi", "rached ghannouchi",
            "bourguiba", "ben ali", "zine el abidine",
            "marzouki", "moncef marzouki",
            "caid essebsi", "beji caid",
            "hamma hammami", "abir moussi",

            // --- Vocabulaire politique explicite ---
            "election presidentielle", "election legislative",
            "coup d'etat", "coup detat",
            "dictature", "dictateur", "regime",
            "revolution", "contre-revolution",
            "manifestation politique", "sit-in politique",
            "propagande", "propaganda",

            // --- Généraux (conflits, idéologies) ---
            "communisme", "communiste",
            "fascisme", "fasciste",
            "nazisme", "nazi",
            "zionisme", "sioniste", "sionism", "zionist",
            "hamas", "hezbollah",
            "daech", "isis", "al qaeda", "al-qaeda",

            // --- Anglais ---
            "trump", "biden", "putin", "zelensky", "netanyahu",
            "political party", "election fraud",

            // --- Dialecte arabe translittéré ---
            "siyesa", "siyasa", "7okouma", "hokouma",
            "ra2is", "rais el joumhouriya",
            "barlamen", "barlament"
    );

    // ─── Catégorie RELIGION / HAINE RELIGIEUSE ──────────────────────────────
    // On ne bloque pas les simples mentions ; on cible les contextes
    // polémiques et les insultes religieuses. Les mots isolés comme
    // "islam" ou "christianisme" ne doivent PAS bloquer à eux seuls,
    // mais combinés à d'autres signaux haineux ils seront attrapés.
    private static final List<String> RELIGIOUS_HATE_KEYWORDS = Arrays.asList(
            // Insultes et slogans haineux
            "mort aux juifs", "mort aux musulmans", "mort aux chretiens",
            "kill the jews", "kill the muslims", "kill the christians",
            "islamophobe", "islamophobie",
            "antisemite", "antisémite", "antisemitism",
            "kafir", "kouffar", "kouffars",
            "mourtad", "murtad",
            "jihad", "djihad",
            "takfir", "takfiri",
            "apostat", "apostasie"
    );

    // ─── Catégorie VIOLENCE / APPEL AU MEURTRE ──────────────────────────────
    private static final List<String> VIOLENCE_KEYWORDS = Arrays.asList(
            "tuer", "egorger", "égorger", "assassiner", "massacrer",
            "kill", "murder", "slaughter", "behead",
            "bomb", "bombe", "explosif", "attentat",
            "terroriste", "terrorist", "terrorism",
            "menace de mort", "death threat",
            "lynchage", "lynching"
    );

    // ─── Catégorie CONTENU ADULTE / PORNOGRAPHIE ────────────────────────────
    private static final List<String> ADULT_KEYWORDS = Arrays.asList(
            "pornographie", "pornographique", "porno", "porn",
            "xxx", "xnxx", "pornhub",
            "escort", "prostituee", "prostituée",
            "nude", "nudes", "sextape"
    );

    // ─── Catégorie DROGUE ───────────────────────────────────────────────────
    private static final List<String> DRUG_KEYWORDS = Arrays.asList(
            "cocaine", "cocaïne", "heroine", "héroïne",
            "cannabis", "marijuana", "zatla", "9anba",
            "ecstasy", "lsd", "meth", "crystal meth",
            "dealer de drogue", "trafic de drogue"
    );

    /** Représente un groupe (nom lisible + patterns compilés). */
    private static final class Category {
        final String label;
        final List<Pattern> strict;
        final List<Pattern> fuzzy;

        Category(String label, List<Pattern> strict, List<Pattern> fuzzy) {
            this.label = label;
            this.strict = strict;
            this.fuzzy = fuzzy;
        }
    }

    /** Résultat d'une vérification : bloqué ou non, avec détails. */
    public static final class ModerationResult {
        private final boolean blocked;
        private final List<String> categories;
        private final List<String> matchedKeywords;

        public ModerationResult(boolean blocked, List<String> categories, List<String> matchedKeywords) {
            this.blocked = blocked;
            this.categories = categories;
            this.matchedKeywords = matchedKeywords;
        }
        public boolean isBlocked() { return blocked; }
        public List<String> getCategories() { return categories; }
        public List<String> getMatchedKeywords() { return matchedKeywords; }

        /** Message utilisateur traduit en français, prêt à renvoyer au client. */
        public String toUserMessage() {
            if (!blocked) return "";
            String cat = String.join(", ", categories);
            return "Publication refusée : contenu détecté comme " + cat
                    + ". CityVoice est une plateforme citoyenne et ne publie "
                    + "pas de contenu de cette nature. Merci de reformuler.";
        }
    }

    private final List<Category> categories;

    public ContentModerationService() {
        this.categories = List.of(
                buildCategory("politique", POLITICAL_KEYWORDS),
                buildCategory("haine religieuse", RELIGIOUS_HATE_KEYWORDS),
                buildCategory("violence", VIOLENCE_KEYWORDS),
                buildCategory("contenu adulte", ADULT_KEYWORDS),
                buildCategory("drogue", DRUG_KEYWORDS)
        );
    }

    private Category buildCategory(String label, List<String> words) {
        List<Pattern> strict = new ArrayList<>();
        List<Pattern> fuzzy = new ArrayList<>();
        for (String raw : words) {
            String norm = normalizeWord(raw);
            if (norm.isBlank()) continue;
            // Pattern strict : le mot doit être entouré de non-lettres.
            strict.add(Pattern.compile(
                    "(?<![\\p{L}])" + Pattern.quote(norm) + "(?![\\p{L}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ));
            // Pattern flou : accepte la répétition des lettres (f+u+c+k+).
            String letters = norm.replaceAll("[^a-z]", "");
            if (letters.length() >= 4) {
                StringBuilder sb = new StringBuilder();
                for (char c : letters.toCharArray()) {
                    sb.append(c).append('+');
                }
                fuzzy.add(Pattern.compile(sb.toString(),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }
        }
        return new Category(label, strict, fuzzy);
    }

    /** Minuscules + diacritiques retirés. */
    private static String normalizeWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /** Normalise un texte utilisateur pour la détection. */
    private static String normalize(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        String stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Remplacer aussi quelques leet courants
        StringBuilder sb = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            switch (c) {
                case '0' -> sb.append('o');
                case '1' -> sb.append('i');
                case '3' -> sb.append('e');
                case '4' -> sb.append('a');
                case '5' -> sb.append('s');
                case '7' -> sb.append('t');
                case '@' -> sb.append('a');
                case '$' -> sb.append('s');
                default  -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ─── API publique ──────────────────────────────────────────────────────

    /**
     * Vérifie un ou plusieurs champs de texte (titre + contenu, par exemple).
     * Retourne un {@link ModerationResult}. Si bloqué, {@code categories}
     * contient les catégories détectées et {@code matchedKeywords} la liste
     * exacte des termes offensants (utile pour les logs).
     */
    public ModerationResult checkContent(String... texts) {
        if (texts == null || texts.length == 0) {
            return new ModerationResult(false, List.of(), List.of());
        }
        StringBuilder all = new StringBuilder();
        for (String t : texts) {
            if (t != null && !t.isBlank()) {
                all.append(' ').append(t);
            }
        }
        String combined = all.toString();
        if (combined.isBlank()) {
            return new ModerationResult(false, List.of(), List.of());
        }

        String normalized = normalize(combined);
        String lettersOnly = normalized.replaceAll("[^a-z]", "");

        List<String> hitCategories = new ArrayList<>();
        List<String> hitKeywords = new ArrayList<>();

        for (Category cat : categories) {
            boolean hit = false;
            for (Pattern p : cat.strict) {
                Matcher m = p.matcher(normalized);
                if (m.find()) {
                    hit = true;
                    hitKeywords.add(m.group());
                    break;
                }
            }
            if (!hit) {
                for (Pattern p : cat.fuzzy) {
                    Matcher m = p.matcher(lettersOnly);
                    if (m.find()) {
                        hit = true;
                        hitKeywords.add(m.group());
                        break;
                    }
                }
            }
            if (hit && !hitCategories.contains(cat.label)) {
                hitCategories.add(cat.label);
            }
        }

        boolean blocked = !hitCategories.isEmpty();
        if (blocked) {
            LOGGER.info("[ContentModerationService] Publication bloquée — catégories : {} — termes : {}",
                    hitCategories, hitKeywords);
        }
        return new ModerationResult(blocked, hitCategories, hitKeywords);
    }

    /** Raccourci boolean : true si au moins une catégorie est déclenchée. */
    public boolean isBlocked(String... texts) {
        return checkContent(texts).isBlocked();
    }
}
