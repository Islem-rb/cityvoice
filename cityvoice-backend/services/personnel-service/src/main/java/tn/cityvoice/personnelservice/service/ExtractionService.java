package tn.cityvoice.personnelservice.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;

@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    /** Limite de caractères safe pour bge-m3 */
    private static final int MAX_CHARS = 3500;

    /**
     * Extrait le texte depuis un PDF CV.
     */
    public String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new RuntimeException("Fichier PDF vide");
        }
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String raw = stripper.getText(document);
            log.debug("PDF extrait : {} chars bruts", raw.length());
            String cleaned = cleanText(raw);
            log.debug("Après nettoyage : {} chars", cleaned.length());
            return cleaned;
        } catch (Exception e) {
            throw new RuntimeException("Erreur extraction PDF", e);
        }
    }

    /**
     * Nettoyage en 5 étapes pour éliminer tout caractère pouvant causer
     * un NaN dans bge-m3 :
     *
     *  1. NFD  → décompose les accents français (é→e+´, ç→c+¸)
     *  2. Supprime les diacritiques (combining marks) → é devient e
     *  3. Remplacements sémantiques (tirets, guillemets, puces → ASCII)
     *  4. Filtre ASCII strict (32–126) + \n \t : tout le reste → espace
     *     → élimine PUA (puces PDF \uF0B7…), symboles graphiques, etc.
     *  5. Normalise les espaces et limite à MAX_CHARS
     *
     * Résultat garanti : chaîne 100% ASCII, zéro NaN possible pour bge-m3.
     */
    public String cleanText(String text) {
        if (text == null || text.isBlank()) return "";

        // ── Étape 1 & 2 : NFD + suppression diacritiques ─────────────────
        // é (U+00E9) → e (U+0065) + ´ (U+0301) → on supprime U+0301 → "e"
        // ç (U+00E7) → c (U+0063) + ¸ (U+0327) → on supprime U+0327 → "c"
        String s = Normalizer.normalize(text, Normalizer.Form.NFD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}", "");

        // ── Étape 3 : remplacements sémantiques → ASCII ───────────────────
        s = s
                // Tirets typographiques
                .replace("\u2013", "-")   // en dash –
                .replace("\u2014", "-")   // em dash —
                .replace("\u2212", "-")   // minus sign −
                // Guillemets
                .replace("\u201C", "\"")  // "
                .replace("\u201D", "\"")  // "
                .replace("\u2018", "'")   // '
                .replace("\u2019", "'")   // '
                .replace("\u00AB", "\"")  // «
                .replace("\u00BB", "\"")  // »
                // Puces et symboles graphiques
                .replace("\u2022", "-")   // •
                .replace("\u25A0", "-")   // ■
                .replace("\u25CF", "-")   // ●
                .replace("\u25AA", "-")   // ▪
                .replace("\u25AB", "-")   // ▫
                .replace("\u2027", "-")   // ‧
                .replace("\u25B6", ">")   // ▶
                // Ligatures latines (PDFBox les extrait parfois)
                .replace("\uFB01", "fi")  // ﬁ
                .replace("\uFB02", "fl")  // ﬂ
                .replace("\uFB03", "ffi") // ﬃ
                .replace("\uFB04", "ffl") // ﬄ
                // Espaces spéciaux → espace normal
                .replace("\u00A0", " ")   // espace insécable
                .replace("\u202F", " ")   // narrow no-break space
                .replace("\u2009", " ")   // thin space
                // Ellipses
                .replace("\u2026", "...");

        // ── Étape 4 : filtre ASCII strict ────────────────────────────────
        // Garde uniquement : espace→tilde (32-126) + \n + \t + \r
        // Tout le reste (PUA U+E000–U+F8FF, symboles, etc.) → espace
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 32 && c <= 126) || c == '\n' || c == '\t' || c == '\r') {
                sb.append(c);
            } else {
                sb.append(' '); // remplacer par espace pour ne pas coller les mots
            }
        }
        s = sb.toString();

        // ── Étape 5 : normaliser les espaces + limiter ────────────────────
        s = s.replaceAll("[ \\t]+", " ")                    // espaces multiples
                .replaceAll("(\\r?\\n[ \\t]*){3,}", "\n\n")    // lignes vides >2 → 2
                .trim();

        if (s.length() > MAX_CHARS) {
            // Couper proprement sur un espace
            int cut = s.lastIndexOf(' ', MAX_CHARS);
            s = s.substring(0, cut > 0 ? cut : MAX_CHARS);
        }

        return s;
    }
}