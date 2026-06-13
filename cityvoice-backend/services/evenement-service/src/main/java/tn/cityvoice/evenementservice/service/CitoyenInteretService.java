package tn.cityvoice.evenementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import tn.cityvoice.evenementservice.entity.CitoyenInteret;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.repository.CitoyenInteretRepository;
import tn.cityvoice.evenementservice.repository.EvenementRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CitoyenInteretService {

    private final CitoyenInteretRepository interetRepository;
    private final EvenementRepository evenementRepository;
    private final WebClient webClient;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.model}")
    private String groqModel;

    // ── Toggle intérêt ────────────────────────────────
    public boolean toggleInteret(String citoyenId, Long evenementId) {
        if (interetRepository.existsByCitoyenIdAndEvenementId(citoyenId, evenementId)) {
            interetRepository.deleteByCitoyenIdAndEvenementId(citoyenId, evenementId);
            log.info("Intérêt retiré : {} → {}", citoyenId, evenementId);
            return false; // retiré
        } else {
            Evenement ev = evenementRepository.findById(evenementId)
                    .orElseThrow(() -> new RuntimeException("Événement introuvable"));

            CitoyenInteret interet = new CitoyenInteret();
            interet.setCitoyenId(citoyenId);
            interet.setEvenementId(evenementId);
            interet.setTypeEvenement(ev.getType().name());
            interetRepository.save(interet);

            log.info("Intérêt ajouté : {} → {}", citoyenId, evenementId);
            return true; // ajouté
        }
    }

    // ── Get IDs likés ─────────────────────────────────
    @Transactional(readOnly = true)
    public List<Long> getInterets(String citoyenId) {
        return interetRepository.findEvenementIdsByCitoyenId(citoyenId);
    }

    // ── Recommandations via Groq AI ───────────────────
    @Transactional(readOnly = true)
    public List<Long> getRecommandations(String citoyenId) {
        // 1. Récupérer les types les plus likés
        List<Object[]> topTypes = interetRepository.findTopTypesByCitoyenId(citoyenId);
        if (topTypes.isEmpty()) return Collections.emptyList();

        // 2. Construire le résumé des intérêts
        String resumeInterets = topTypes.stream()
                .map(row -> row[0] + " (" + row[1] + " fois)")
                .collect(Collectors.joining(", "));

        // 3. Récupérer tous les événements publiés
        List<Evenement> evenements = evenementRepository
                .findByStatutOrderByDateDebutAsc(
                        tn.cityvoice.evenementservice.enums.StatutEvenement.PUBLIE);

        if (evenements.isEmpty()) return Collections.emptyList();

        // 4. IDs déjà likés → ne pas recommander
        List<Long> dejaLikes = interetRepository.findEvenementIdsByCitoyenId(citoyenId);

        // 5. Candidats à recommander
        String candidats = evenements.stream()
                .filter(ev -> !dejaLikes.contains(ev.getId()))
                .map(ev -> ev.getId() + ":" + ev.getType().name() + ":" + ev.getTitre())
                .collect(Collectors.joining("\n"));

        if (candidats.isEmpty()) return Collections.emptyList();

        // 6. Prompt Groq
        String prompt = """
                Un utilisateur a montré de l'intérêt pour ces types d'événements : %s
                
                Voici les événements disponibles (format id:type:titre) :
                %s
                
                Recommande exactement 3 événements les plus pertinents.
                Réponds UNIQUEMENT avec les IDs séparés par des virgules.
                Exemple : 12,45,78
                """.formatted(resumeInterets, candidats);

        try {
            // 7. Appel Groq
            Map<String, Object> requestBody = Map.of(
                    "model", groqModel,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "max_tokens", 50,
                    "temperature", 0.3
            );

            Map response = webClient.post()
                    .uri(groqApiUrl)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            // 8. Parser les IDs
            String content = ((Map<String, Object>)
                    ((List<?>) response.get("choices")).stream()
                            .findFirst().get())
                    .get("message").toString();

            // Extraire le texte
            String texte = (String) ((Map<?, ?>) ((Map<?, ?>)
                    ((List<?>) response.get("choices")).get(0))
                    .get("message")).get("content");

            return Arrays.stream(texte.trim().split(","))
                    .map(String::trim)
                    .filter(s -> s.matches("\\d+"))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Erreur Groq recommandations : {}", e.getMessage());
            // Fallback : recommander selon le type le plus liké
            String topType = (String) topTypes.get(0)[0];
            return evenements.stream()
                    .filter(ev -> ev.getType().name().equals(topType))
                    .filter(ev -> !dejaLikes.contains(ev.getId()))
                    .limit(3)
                    .map(Evenement::getId)
                    .collect(Collectors.toList());
        }
    }
}