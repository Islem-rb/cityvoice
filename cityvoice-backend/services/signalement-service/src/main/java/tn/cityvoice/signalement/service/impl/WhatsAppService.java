package tn.cityvoice.signalement.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service WhatsApp — Green API (gratuit 3 mois, puis ~5$/mois)
 *
 * Setup (une seule fois côté serveur) :
 *  1. Créer un compte sur https://green-api.com
 *  2. Créer une instance → scanner le QR code avec ton numéro WhatsApp
 *  3. Copier idInstance et apiTokenInstance
 *  4. Renseigner dans application.properties (ou variables d'env) :
 *       greenapi.instance-id=XXXXXXXXXX
 *       greenapi.api-token=XXXXXXXXXXXXXXXXXXXXXXXXXX
 *       greenapi.enabled=true
 *
 * Les utilisateurs n'ont RIEN à configurer — seul leur numéro de téléphone
 * (déjà stocké dans leur profil) est nécessaire.
 *
 * API : POST https://api.greenapi.com/waInstance{id}/sendMessage/{token}
 *       Body : { "chatId": "21652426598@c.us", "message": "..." }
 */
@Service
@Slf4j
public class WhatsAppService {

    // URL construite dynamiquement (pas de template variable pour éviter l'encodage URI)

    @Value("${greenapi.api-url:https://api.greenapi.com}")
    private String apiUrl;

    @Value("${greenapi.instance-id:}")
    private String instanceId;

    @Value("${greenapi.api-token:}")
    private String apiToken;

    @Value("${greenapi.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envoie un message WhatsApp via Green API.
     *
     * @param toNumber  numéro destinataire (local 8 chiffres ou international)
     * @param message   texte à envoyer
     */
    public void send(String toNumber, String message) {
        if (!enabled) {
            log.debug("[GreenAPI] Désactivé — message non envoyé à {}", toNumber);
            return;
        }
        if (instanceId == null || instanceId.isBlank()
                || apiToken  == null || apiToken.isBlank()
                || apiUrl    == null || apiUrl.isBlank()) {
            log.warn("[GreenAPI] Credentials manquants (api-url, instance-id ou api-token)");
            return;
        }
        if (toNumber == null || toNumber.isBlank()) {
            log.debug("[GreenAPI] Numéro vide — message ignoré");
            return;
        }

        // Nettoyer le numéro : garder uniquement les chiffres
        String clean = toNumber.replaceAll("[^0-9]", "");
        // Ajouter indicatif Tunisie si numéro local (8 chiffres)
        if (clean.length() == 8) clean = "216" + clean;

        // Format chatId Green API : {phone}@c.us
        String chatId = clean + "@c.us";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                "chatId",  chatId,
                "message", message
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // Construire l'URL en string direct — évite l'encodage URI des template variables
            String url = apiUrl + "/waInstance" + instanceId + "/sendMessage/" + apiToken;
            log.info("[GreenAPI] → POST {} | chatId={}", url.replaceAll("/.{20,}$", "/***"), chatId);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[GreenAPI] ✅ Message envoyé à +{}", clean);
            } else {
                log.warn("[GreenAPI] ❌ Réponse inattendue {} : {}", response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("[GreenAPI] Erreur envoi à +{} : {}", clean, e.getMessage());
        }
    }

    /**
     * Labels lisibles pour chaque type de signalement (avec accents — WhatsApp
     * supporte l'UTF-8 contrairement aux SMS GSM-7).
     */
    private static final java.util.Map<String, String> TYPE_LABELS = java.util.Map.of(
        "TROU_CHAUSSEE",          "Trou sur la chaussée",
        "LAMPADAIRE_CASSE",       "Lampadaire cassé",
        "FUITE_EAU",              "Fuite d'eau",
        "DECHETS_NON_COLLECTES",  "Déchets non collectés",
        "POTEAU_ENDOMMAGE",       "Poteau endommagé",
        "SIGNALISATION_MANQUANTE","Signalisation manquante",
        "CANIVEAU_BOUCHE",        "Caniveau bouché",
        "ESPACE_VERT_DEGRADE",    "Espace vert dégradé",
        "AUTRE",                  "Incident"
    );

    /**
     * Formatte et envoie la notification de changement de statut avec adresse.
     */
    public void notifierChangementStatut(String telephone, Long sigId,
                                          String ancienStatut, String nouveauStatut,
                                          String type, String adresse) {
        String emoji = switch (nouveauStatut) {
            case "EN_COURS" -> "🔧";
            case "RESOLU"   -> "✅";
            case "REJETE"   -> "❌";
            default          -> "ℹ️";
        };

        String statutLabel = switch (nouveauStatut) {
            case "EN_ATTENTE" -> "En attente";
            case "EN_COURS"   -> "En cours de traitement";
            case "RESOLU"     -> "Résolu";
            case "REJETE"     -> "Rejeté";
            default            -> nouveauStatut;
        };

        String typeLabel = TYPE_LABELS.getOrDefault(
            type != null ? type.toUpperCase() : "AUTRE", "Incident");

        String lieuLigne = (adresse != null && !adresse.isBlank())
            ? String.format("📍 *Lieu* : %s\n", adresse)
            : "";

        String msg = String.format(
            "%s *Madina* — Mise à jour de votre signalement\n\n" +
            "📋 *Type* : %s\n" +
            "%s" +
            "🔖 *Statut* : *%s*\n\n" +
            "Merci de contribuer à l'amélioration de votre ville 🏙️",
            emoji, typeLabel, lieuLigne, statutLabel
        );

        send(telephone, msg);
    }

    /**
     * Ancienne signature maintenue pour compatibilité — appelle la nouvelle sans adresse.
     */
    public void notifierChangementStatut(String telephone, Long sigId,
                                          String ancienStatut, String nouveauStatut,
                                          String type) {
        notifierChangementStatut(telephone, sigId, ancienStatut, nouveauStatut, type, null);
    }
}
