package tn.cityvoice.signalement.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Service SMS — canal alternatif à WhatsApp pour les citoyens qui préfèrent
 * recevoir un SMS (ou n'ont pas WhatsApp installé).
 *
 * Providers supportés :
 *   - vonage   : Vonage Messages API (trial gratuit ~2€ de crédit ≈ 30 SMS)
 *   - textbelt : TextBelt (1 SMS/jour gratuit, actuellement bloqué par Cloudflare)
 *   - log      : mode démo, imprime le SMS en console (aucun envoi réel)
 *
 * Config dans application.properties :
 *   sms.enabled=true
 *   sms.provider=vonage
 *   sms.vonage.url=https://api.nexmo.com/v1/messages
 *   sms.vonage.api-key=...
 *   sms.vonage.api-secret=...
 *   sms.vonage.from=Vonage APIs
 *
 * L'utilisateur n'a RIEN à configurer — seul son numéro (déjà dans son profil)
 * et son toggle `smsNotifs=true` sont nécessaires.
 */
@Service
@Slf4j
public class SmsService {

    @Value("${sms.enabled:false}")
    private boolean enabled;

    @Value("${sms.provider:log}")
    private String provider;

    @Value("${sms.textbelt.url:https://textbelt.com/text}")
    private String textbeltUrl;

    @Value("${sms.textbelt.key:textbelt}")
    private String textbeltKey;

    @Value("${sms.vonage.url:https://api.nexmo.com/v1/messages}")
    private String vonageUrl;

    @Value("${sms.vonage.api-key:}")
    private String vonageApiKey;

    @Value("${sms.vonage.api-secret:}")
    private String vonageApiSecret;

    @Value("${sms.vonage.from:Vonage APIs}")
    private String vonageFrom;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envoie un SMS au numéro donné.
     *
     * @param toNumber numéro destinataire (local 8 chiffres ou international)
     * @param message  texte à envoyer (max ~160 caractères recommandé)
     */
    public void send(String toNumber, String message) {
        if (!enabled) {
            log.debug("[SMS] Désactivé — message non envoyé à {}", toNumber);
            return;
        }
        if (toNumber == null || toNumber.isBlank()) {
            log.debug("[SMS] Numéro vide — message ignoré");
            return;
        }

        // Nettoyer le numéro : garder uniquement les chiffres
        String clean = toNumber.replaceAll("[^0-9]", "");
        // Ajouter indicatif Tunisie si numéro local (8 chiffres)
        if (clean.length() == 8) clean = "216" + clean;

        switch (provider.toLowerCase()) {
            case "vonage"   -> sendViaVonage(clean, message);
            case "textbelt" -> sendViaTextbelt(clean, message);
            case "disabled", "log" -> logSimulatedSend(clean, message);
            default -> log.warn("[SMS] Provider '{}' non supporté — message non envoyé", provider);
        }
    }

    /**
     * Envoi via Vonage Messages API (POST JSON + Basic Auth).
     *
     * Endpoint  : https://api.nexmo.com/v1/messages
     * Auth      : Basic <base64(api_key:api_secret)>
     * Body      : { "to": "21652426598", "from": "Vonage APIs",
     *              "channel": "sms", "message_type": "text", "text": "..." }
     * Réponse   : { "message_uuid": "..." } (202 Accepted)
     *
     * Note : le 'to' doit être au format international SANS le '+' (ex: 21652426598).
     * En trial, seuls les numéros vérifiés dans le dashboard Vonage reçoivent les SMS.
     */
    private void sendViaVonage(String phone, String message) {
        if (vonageApiKey == null || vonageApiKey.isBlank()
         || vonageApiSecret == null || vonageApiSecret.isBlank()) {
            log.warn("[SMS:vonage] API key/secret manquants — fallback log");
            logSimulatedSend(phone, message);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            headers.setBasicAuth(vonageApiKey, vonageApiSecret);

            // Escape JSON-safe : \, ", newlines
            String safeText = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
            String safeFrom = vonageFrom
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

            String bodyJson = String.format(
                "{\"to\":\"%s\",\"from\":\"%s\",\"channel\":\"sms\"," +
                "\"message_type\":\"text\",\"text\":\"%s\"}",
                phone, safeFrom, safeText
            );

            HttpEntity<String> request = new HttpEntity<>(bodyJson, headers);

            log.info("[SMS:vonage] → POST {} | to={} from='{}'", vonageUrl, phone, vonageFrom);
            ResponseEntity<String> response =
                restTemplate.postForEntity(vonageUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[SMS:vonage] ✅ Envoyé à +{} — {}", phone, response.getBody());
            } else {
                log.warn("[SMS:vonage] ❌ HTTP {} : {}", response.getStatusCode(), response.getBody());
            }

        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            log.error("[SMS:vonage] ❌ HTTP {} — {}",
                httpEx.getStatusCode(), httpEx.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[SMS:vonage] Erreur envoi à +{} : {}", phone, e.getMessage(), e);
        }
    }

    /**
     * Mode démo : simule un envoi SMS avec une trace visible et pro.
     * Utile pour le PFE et les tests sans coût.
     */
    private void logSimulatedSend(String phone, String message) {
        String border = "═".repeat(62);
        log.info("\n╔{}╗", border);
        log.info("║  📱 SMS GATEWAY — MODE DÉMO (aucun envoi réel)              ║");
        log.info("╠{}╣", border);
        log.info("║  Destinataire : +{}                                ", phone);
        log.info("║  Message      : {}", message);
        log.info("║  Statut       : ✅ Simulé (provider=log)                    ║");
        log.info("╚{}╝\n", border);
    }

    /**
     * Envoi via TextBelt (POST form-urlencoded).
     * Réponse JSON : { "success": true|false, "textId": "...", "quotaRemaining": N }
     */
    private void sendViaTextbelt(String phone, String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("phone",   "+" + phone);
            body.add("message", message);
            body.add("key",     textbeltKey);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.info("[SMS:textbelt] → POST {} | phone=+{}", textbeltUrl, phone);
            ResponseEntity<String> response =
                restTemplate.postForEntity(textbeltUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String respBody = response.getBody() != null ? response.getBody() : "";
                if (respBody.contains("\"success\":true")) {
                    log.info("[SMS:textbelt] ✅ Envoyé à +{} — {}", phone, respBody);
                } else {
                    log.warn("[SMS:textbelt] ❌ Échec ou quota dépassé : {}", respBody);
                }
            } else {
                log.warn("[SMS:textbelt] ❌ HTTP {} : {}", response.getStatusCode(), response.getBody());
            }

        } catch (Exception e) {
            log.error("[SMS:textbelt] Erreur envoi à +{} : {}", phone, e.getMessage());
        }
    }

    /**
     * Labels lisibles pour chaque type de signalement (sans accents pour éviter
     * les problèmes d'encodage GSM-7 sur les SMS vers la Tunisie).
     */
    private static final java.util.Map<String, String> TYPE_LABELS = java.util.Map.of(
        "TROU_CHAUSSEE",          "trou sur la chaussee",
        "LAMPADAIRE_CASSE",       "lampadaire casse",
        "FUITE_EAU",              "fuite d'eau",
        "DECHETS_NON_COLLECTES",  "dechets non collectes",
        "POTEAU_ENDOMMAGE",       "poteau endommage",
        "SIGNALISATION_MANQUANTE","signalisation manquante",
        "CANIVEAU_BOUCHE",        "caniveau bouche",
        "ESPACE_VERT_DEGRADE",    "espace vert degrade",
        "AUTRE",                  "incident"
    );

    /**
     * Formatte et envoie la notification de changement de statut.
     * Format : "CityVoice: [type] situe a [adresse] est maintenant [statut]. Merci !"
     * Tronque l'adresse si nécessaire pour tenir en ~160 caractères.
     */
    public void notifierChangementStatut(String telephone, Long sigId,
                                          String ancienStatut, String nouveauStatut,
                                          String type, String adresse) {
        String statutLabel = switch (nouveauStatut) {
            case "EN_ATTENTE" -> "en attente";
            case "EN_COURS"   -> "en cours";
            case "RESOLU"     -> "resolu";
            case "REJETE"     -> "rejete";
            default            -> nouveauStatut.toLowerCase();
        };

        String typeLabel = TYPE_LABELS.getOrDefault(
            type != null ? type.toUpperCase() : "AUTRE", "incident");

        String msg;
        if (adresse != null && !adresse.isBlank()) {
            String adresseCourt = adresse.length() > 60 ? adresse.substring(0, 60) + "..." : adresse;
            msg = String.format(
                "CityVoice: votre signalement \"%s\" situe a %s est maintenant %s. Merci !",
                typeLabel, adresseCourt, statutLabel
            );
        } else {
            msg = String.format(
                "CityVoice: votre signalement \"%s\" est maintenant %s. Merci !",
                typeLabel, statutLabel
            );
        }

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
