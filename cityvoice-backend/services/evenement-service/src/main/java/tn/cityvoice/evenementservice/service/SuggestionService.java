package tn.cityvoice.evenementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.evenementservice.dto.request.SuggestionRequest;
import tn.cityvoice.evenementservice.dto.response.SuggestionAnalyseResponse;
import tn.cityvoice.evenementservice.dto.response.SuggestionResponse;
import tn.cityvoice.evenementservice.entity.EvenementNotification;
import tn.cityvoice.evenementservice.entity.Suggestion;
import tn.cityvoice.evenementservice.repository.SuggestionRepository;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j

public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final GeminiService geminiService;
    private final EvenementNotificationService notificationService;

    public SuggestionResponse soumettreSuggestion(SuggestionRequest req) {
        Suggestion suggestion = Suggestion.builder()
                .titre(req.getTitre())
                .description(req.getDescription())
                .typeSouhaite(req.getTypeSouhaite())
                .lieuSouhaite(req.getLieuSouhaite())
                .dateSouhaitee(req.getDateSouhaitee())
                .citoyenId(req.getCitoyenId())
                .emailCitoyen(req.getEmailCitoyen())
                .build();
        Suggestion saved = suggestionRepository.save(suggestion);
        log.info("Suggestion soumise par citoyen {}", req.getCitoyenId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> listerToutes() {
        return suggestionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> listerParStatut(String statut) {
        return suggestionRepository.findByStatut(statut).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> listerParCitoyen(String citoyenId) {
        return suggestionRepository.findByCitoyenId(citoyenId).stream().map(this::toResponse).toList();
    }

    public SuggestionResponse modifierSuggestion(Long id, SuggestionRequest req) {
        Suggestion s = findById(id);
        if (!"SOUMISE".equals(s.getStatut()))
            throw new RuntimeException("Impossible de modifier une suggestion déjà traitée");
        s.setTitre(req.getTitre());
        s.setDescription(req.getDescription());
        s.setTypeSouhaite(req.getTypeSouhaite());
        s.setLieuSouhaite(req.getLieuSouhaite());
        s.setDateSouhaitee(req.getDateSouhaitee());
        log.info("Suggestion {} modifiée", id);
        return toResponse(suggestionRepository.save(s));
    }

    public void supprimerSuggestion(Long id) {
        Suggestion s = findById(id);
        if (!"SOUMISE".equals(s.getStatut()))
            throw new RuntimeException("Impossible de supprimer une suggestion déjà traitée");
        suggestionRepository.delete(s);
        log.info("Suggestion {} supprimée", id);
    }

    public SuggestionResponse traiterSuggestion(Long id, String statut, String commentaire) {
        Suggestion s = findById(id);
        s.setStatut(statut);
        s.setCommentaireAdmin(commentaire);
        // Dans traiterSuggestion()
        EvenementNotification.TypeNotification type = "ACCEPTEE".equals(statut)
                ? EvenementNotification.TypeNotification.SUGGESTION_ACCEPTEE
                : EvenementNotification.TypeNotification.SUGGESTION_REJETEE;

        String titre = "ACCEPTEE".equals(statut)
                ? "Suggestion acceptée ✅"
                : "Suggestion rejetée ❌";

        notificationService.creer(
                s.getCitoyenId(),
                titre,
                commentaire,
                type
        );
        log.info("Suggestion {} traitée : {}", id, statut);
        return toResponse(suggestionRepository.save(s));
    }

    public SuggestionAnalyseResponse analyserAvecAI(Long id) {
        Suggestion s = findById(id);
        log.info("Analyse AI de la suggestion {}", id);
        return geminiService.analyserSuggestion(s);
    }

    private Suggestion findById(Long id) {
        return suggestionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Suggestion introuvable : " + id));
    }

    private SuggestionResponse toResponse(Suggestion s) {
        return SuggestionResponse.builder()
                .id(s.getId())
                .titre(s.getTitre())
                .description(s.getDescription())
                .typeSouhaite(s.getTypeSouhaite())
                .lieuSouhaite(s.getLieuSouhaite())
                .dateSouhaitee(s.getDateSouhaitee())
                .citoyenId(s.getCitoyenId())
                .emailCitoyen(s.getEmailCitoyen())
                .statut(s.getStatut())
                .commentaireAdmin(s.getCommentaireAdmin())
                .soumisLe(s.getSoumisLe())
                .build();
    }
    // ── Appel Ollama Llama3.2 (rapide) ────────────────
    private String appellerLlama(String prompt) {
        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(30000); // 30s max
            RestTemplate rt = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> options = new HashMap<>();
            options.put("num_predict", 200);
            options.put("temperature", 0.7);

            Map<String, Object> body = new HashMap<>();
            body.put("model",   "llama3.2");
            body.put("prompt",  prompt);
            body.put("stream",  false);
            body.put("options", options);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response =
                    rt.exchange(
                            "http://localhost:11434/api/generate",
                            HttpMethod.POST,
                            request,
                            new ParameterizedTypeReference<Map<String, Object>>() {}
                    );

            if (response.getBody() != null) {
                String result = (String) response.getBody().get("response");
                return result != null ? result.trim() : "";
            }
        } catch (Exception e) {
            log.error("Erreur Llama: {}", e.getMessage());
        }
        return "";
    }

    // ── Générer justification IA ───────────────────────
    public String genererJustification(Long id, String statut) {
        Suggestion s = findById(id);
        if (s.getTitre() == null || s.getTitre().trim().length() < 5) {
            return "ACCEPTEE".equals(statut)
                    ? "Chère citoyenne / Cher citoyen,\n\nVotre suggestion a été acceptée par notre équipe. Nous vous contacterons prochainement.\n\nL'équipe CityVoice"
                    : "Chère citoyenne / Cher citoyen,\n\nVotre suggestion n'a pas pu être traitée en raison d'informations insuffisantes. Nous vous encourageons à resoumettre avec plus de détails.\n\nL'équipe CityVoice";
        }
        String prompt = "ACCEPTEE".equals(statut)
                ? String.format("""
            Rédige une justification officielle courte (3 phrases max)
            pour l'acceptation de cette suggestion citoyenne.
            
            RÈGLES STRICTES:
            - Commence par "Chère citoyenne / Cher citoyen,"
            - NE PAS utiliser [Votre Nom] ou placeholder
            - Mentionne le titre exact de la suggestion
            - Ton chaleureux et professionnel
            - Termine par "L'équipe CityVoice"
            - En français uniquement
            
            Suggestion: %s
            Lieu: %s
            Type: %s
            
            Justification:
            """,
                s.getTitre(),
                s.getLieuSouhaite() != null ? s.getLieuSouhaite() : "non précisé",
                s.getTypeSouhaite() != null ? s.getTypeSouhaite().toString() : "non précisé")

                : String.format("""
            Rédige une justification officielle courte (3 phrases max)
            pour le rejet de cette suggestion citoyenne.
            
            RÈGLES STRICTES:
            - Commence par "Chère citoyenne / Cher citoyen,"
            - NE PAS utiliser [Votre Nom] ou placeholder
            - Mentionne le titre exact de la suggestion
            - Explique brièvement pourquoi
            - Encourage à resoumettre
            - Termine par "L'équipe CityVoice"
            - En français uniquement
            
            Suggestion: %s
            Lieu: %s
            Type: %s
            
            Justification:
            """,
                s.getTitre(),
                s.getLieuSouhaite() != null ? s.getLieuSouhaite() : "non précisé",
                s.getTypeSouhaite() != null ? s.getTypeSouhaite().toString() : "non précisé");

        return appellerLlama(prompt); // ← llama3.2 au lieu de mistral
    }
}