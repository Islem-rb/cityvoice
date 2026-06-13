package tn.cityvoice.personnelservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizzService {

    private static final String OLLAMA_URL   = "http://localhost:11434/api/generate";
    private static final String OLLAMA_MODEL = "phi3:mini";   // ← léger et rapide

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> generateQuiz(String fonction) {
        String prompt = buildPrompt(fonction);

        Map<String, Object> body = new HashMap<>();
        body.put("model", OLLAMA_MODEL);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("format", "json");                 // force JSON valide (Ollama ≥0.1.14)
        body.put("options", Map.of(
                "temperature", 0.1,                 // très bas pour rester cohérent
                "num_predict", 5000,                // marge large pour 10 questions
                "top_p", 0.9,
                "repeat_penalty", 1.15
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(OLLAMA_URL, req, String.class);
            String raw = resp.getBody();
            log.info(">>> [Quiz] Ollama raw response length: {}", raw != null ? raw.length() : 0);

            JsonNode root = objectMapper.readTree(raw);
            // Pour les modèles supportant "format":"json", la réponse est directement du JSON
            String response = root.has("response") ? root.path("response").asText() : root.toString();

            List<Map<String, Object>> questions = parseQuestions(response);
            if (questions == null || questions.isEmpty()) {
                log.warn(">>> [Quiz] Aucune question valide → fallback intelligent");
                return fallbackQuizByFonction(fonction);
            }
            // Si moins de 10 questions, on complète avec le fallback intelligent
            if (questions.size() < 10) {
                log.warn(">>> [Quiz] Seulement {} questions générées, complétion avec fallback", questions.size());
                List<Map<String, Object>> extra = fallbackQuizByFonction(fonction);
                int needed = 10 - questions.size();
                if (extra.size() >= needed) {
                    questions.addAll(extra.subList(0, needed));
                } else {
                    questions.addAll(extra);
                }
            }
            return questions.subList(0, 10);

        } catch (Exception e) {
            log.error(">>> [Quiz] Erreur Ollama : {}", e.getMessage());
            return fallbackQuizByFonction(fonction);
        }
    }

    private String buildPrompt(String fonction) {
        return "Génère exactement 10 questions QCM (4 choix A/B/C/D) pour le métier : \"" + fonction + "\". " +
                "Chaque question doit être technique et propre à ce métier (sécurité, outils, procédures). " +
                "Réponse UNIQUEMENT au format JSON, sans texte avant ou après. " +
                "Exemple : [{\"question\":\"...\",\"options\":[\"A) ...\",\"B) ...\",\"C) ...\",\"D) ...\"],\"correctIndex\":0}]";
    }

    private String repairTruncatedJson(String json) {
        int lastCompleteBrace = -1;
        int braceCount = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) lastCompleteBrace = i;
            }
        }
        if (lastCompleteBrace > 0 && lastCompleteBrace < json.length() - 1) {
            json = json.substring(0, lastCompleteBrace + 1);
        }
        if (!json.trim().endsWith("]")) {
            json = json + "]";
        }
        return json;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQuestions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        String cleaned = text.replaceAll("```json\\s*", "").replaceAll("```", "").trim();
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) return Collections.emptyList();

        String jsonCandidate = cleaned.substring(start, end + 1).trim();
        String fixed = jsonCandidate
                .replaceAll("'([^']+)'\\s*:", "\"$1\":")
                .replaceAll(":\\s*'([^']*)'", ":\"$1\"")
                .replaceAll(",}", "}")
                .replaceAll(",]", "]");

        String repaired = repairTruncatedJson(fixed);
        try {
            List<Map<String, Object>> questions = objectMapper.readValue(repaired, List.class);
            if (questions.size() > 10) questions = questions.subList(0, 10);
            return questions;
        } catch (Exception e) {
            log.error(">>> [Quiz] Parsing échoué : {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ===================== FALLBACK INTELLIGENT (questions pré-définies) =====================
    private List<Map<String, Object>> fallbackQuizByFonction(String fonction) {
        Map<String, List<Map<String, Object>>> banque = new HashMap<>();

        // Exemple pour OUVRIER_SPECIALISTE (vous pouvez ajouter d'autres métiers)
        List<Map<String, Object>> ouvrier = new ArrayList<>();
        ouvrier.add(Map.of("question", "Quel est l'équipement de protection individuelle (EPI) obligatoire pour un ouvrier sur un chantier ?",
                "options", Arrays.asList("A) Casque", "B) Gants", "C) Chaussures de sécurité", "D) Tous les trois"),
                "correctIndex", 3));
        ouvrier.add(Map.of("question", "Que faire en cas de bris de vitre sur une machine-outil ?",
                "options", Arrays.asList("A) Continuer à travailler", "B) Arrêter la machine et prévenir le responsable", "C) Réparer soi-même", "D) Essayer de retirer les morceaux à la main"),
                "correctIndex", 1));
        ouvrier.add(Map.of("question", "À quelle hauteur minimale le port du harnais de sécurité est-il obligatoire ?",
                "options", Arrays.asList("A) 1 mètre", "B) 2 mètres", "C) 3 mètres", "D) 5 mètres"),
                "correctIndex", 2));
        ouvrier.add(Map.of("question", "Quelle couleur identifie une canalisation d'eau dans un bâtiment industriel ?",
                "options", Arrays.asList("A) Rouge", "B) Vert", "C) Bleu", "D) Jaune"),
                "correctIndex", 2));
        ouvrier.add(Map.of("question", "Que signifie le pictogramme tête de mort (crâne) ?",
                "options", Arrays.asList("A) Danger électrique", "B) Substance nocive ou toxique", "C) Risque d'écrasement", "D) Obligation de porter un masque"),
                "correctIndex", 1));
        ouvrier.add(Map.of("question", "Quelle est la première action en cas d'incendie ?",
                "options", Arrays.asList("A) Sauver les personnes", "B) Alerter les pompiers", "C) Essayer d'éteindre", "D) Récupérer ses affaires"),
                "correctIndex", 0));
        ouvrier.add(Map.of("question", "Quel outil utilise-t-on pour mesurer une pièce avec précision ?",
                "options", Arrays.asList("A) Mètre ruban", "B) Pied à coulisse", "C) Règle de chantier", "D) Laser télémètre"),
                "correctIndex", 1));
        ouvrier.add(Map.of("question", "Quelle est la hauteur maximale autorisée pour une échelle sans garde-corps ?",
                "options", Arrays.asList("A) 2 m", "B) 3 m", "C) 5 m", "D) 7 m"),
                "correctIndex", 1));
        ouvrier.add(Map.of("question", "Que signifie l'acronyme 'EPI' ?",
                "options", Arrays.asList("A) Équipement de Protection Individuelle", "B) Équipement de Première Intervention", "C) Évaluation des Postes Inadaptés", "D) Établissement Public d'Inspection"),
                "correctIndex", 0));
        ouvrier.add(Map.of("question", "Quelle est la fréquence recommandée pour la vérification d'un extincteur ?",
                "options", Arrays.asList("A) Tous les mois", "B) Tous les 6 mois", "C) Tous les ans", "D) Tous les 2 ans"),
                "correctIndex", 1));
        banque.put("OUVRIER_SPECIALISTE", ouvrier);

        // Ajoutez ici d'autres métiers (ex: "TECHNICIEN" avec ses propres questions)

        String key = fonction.toUpperCase().trim();
        if (banque.containsKey(key)) {
            return banque.get(key);
        } else {
            return genericFallback(fonction);
        }
    }

    private List<Map<String, Object>> genericFallback(String fonction) {
        List<Map<String, Object>> questions = new ArrayList<>();
        String[][] data = {
                {"Qu'est-ce que la gestion de projet ?", "A) Gérer les finances", "B) Planifier et coordonner des ressources", "C) Rédiger des emails", "D) Former des équipes", "1"},
                {"Quel outil est souvent utilisé pour le suivi des tâches ?", "A) Photoshop", "B) Excel", "C) Jira", "D) Slack", "2"},
                {"Qu'est-ce qu'un sprint dans une équipe Agile ?", "A) Une course rapide", "B) Une réunion hebdomadaire", "C) Une itération de développement courte", "D) Un rapport mensuel", "2"},
                {"Que signifie KPI ?", "A) Key Process Indicator", "B) Key Performance Indicator", "C) Key Product Index", "D) Knowledge Performance Index", "1"},
                {"Qu'est-ce qu'une rétrospective ?", "A) Un retour en arrière historique", "B) Un bilan d'amélioration d'équipe", "C) Un rapport financier", "D) Une revue de code", "1"},
                {"Quel est le rôle du Product Owner ?", "A) Gérer l'infrastructure", "B) Définir les priorités du backlog", "C) Développer les fonctionnalités", "D) Tester l'application", "1"},
                {"Qu'est-ce que le backlog ?", "A) Un fichier de logs", "B) Une liste de bugs", "C) Une liste priorisée de fonctionnalités", "D) Un calendrier d'équipe", "2"},
                {"Comment mesure-t-on la vélocité d'une équipe ?", "A) En km/h", "B) En points de story complétés par sprint", "C) En nombre de réunions", "D) En heures de travail", "1"},
                {"Qu'est-ce qu'un MVP ?", "A) Most Valuable Player", "B) Minimum Viable Product", "C) Maximum Value Project", "D) Most Verified Product", "1"},
                {"Quel est l'objectif d'un stand-up meeting ?", "A) Décider de l'architecture", "B) Synchroniser l'équipe", "C) Faire une démo", "D) Écrire la documentation", "1"}
        };
        for (String[] q : data) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question", q[0] + " [" + fonction + "]");
            m.put("options", Arrays.asList(q[1], q[2], q[3], q[4]));
            m.put("correctIndex", Integer.parseInt(q[5]));
            questions.add(m);
        }
        return questions;
    }
}