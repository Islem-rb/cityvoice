package tn.cityvoice.signalement.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.signalement.config.VoiceDemoProperties;
import tn.cityvoice.signalement.dto.HybridVoiceSessionResponse;
import tn.cityvoice.signalement.dto.JamiMessageRequest;
import tn.cityvoice.signalement.dto.JamiSignalementResponse;
import tn.cityvoice.signalement.dto.SignalementRequest;
import tn.cityvoice.signalement.entity.Signalement;
import tn.cityvoice.signalement.enums.Priorite;
import tn.cityvoice.signalement.enums.TypeSignalement;
import tn.cityvoice.signalement.service.ISignalementService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service hybride pour traiter les appels vocaux depuis 2 sources:
 * 1. WebRTC (navigateur Angular)
 * 2. Jami (app mobile/desktop)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HybridVoiceService {

    private final VoiceDemoProperties properties;
    private final ISignalementService signalementService;
    private final ObjectMapper objectMapper;
    private final VoiceTextCorrector voiceTextCorrector;

    private final RestTemplate restTemplate = new RestTemplate();

    /** RestTemplate avec timeout court, dédié au géocodage */
    private final RestTemplate geoRestTemplate = buildGeoRestTemplate();

    private static RestTemplate buildGeoRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4_000);   // 4s connexion max
        factory.setReadTimeout(6_000);      // 6s lecture max
        return new RestTemplate(factory);
    }
    private final ConcurrentMap<String, HybridVoiceSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String VOICE_UPLOAD_DIR =
        System.getProperty("java.io.tmpdir") + File.separator + "cityvoice-voice";

    /**
     * Reçoit webhooks depuis Web (WebRTC) ou Jami
     * @param sessionId ID de session unique
     * @param source "web" ou "jami"
     * @param step "description" ou "location"
     * @param audioBase64 Audio encodé en Base64
     * @return ID session pour tracking
     */
    public String createOrUpdateSession(String sessionId, String source, String step, String audioBase64, String userId) {
        if (isBlank(audioBase64)) {
            throw new IllegalArgumentException("audioBase64 est requis");
        }
        if (isBlank(step)) {
            throw new IllegalArgumentException("step est requis (description ou location)");
        }

        // Variable finale pour usage dans les lambdas
        final String resolvedSessionId = isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;

        HybridVoiceSession session = sessions.computeIfAbsent(resolvedSessionId, sid -> new HybridVoiceSession(sid, source));

        // Stocker l'ID utilisateur si fourni
        if (!isBlank(userId)) {
            session.userId = userId;
        }

        byte[] audioBytes = decodeBase64Audio(audioBase64);
        log.info("[VOICE-{}] Audio {} reçu pour session {} ({} bytes)",
            source != null ? source.toUpperCase() : "WEB", step, resolvedSessionId, audioBytes.length);

        // Stocker en mémoire (fiable sur tous les OS) + optionnellement sur disque
        if ("description".equalsIgnoreCase(step)) {
            session.descriptionAudioBytes = audioBytes;
            session.descriptionReceived = true;
            tryWriteToDisk(audioBytes, resolvedSessionId + "-description.webm")
                .ifPresent(path -> session.descriptionAudioPath = path);
        } else if ("location".equalsIgnoreCase(step)) {
            session.locationAudioBytes = audioBytes;
            session.locationReceived = true;
            tryWriteToDisk(audioBytes, resolvedSessionId + "-location.webm")
                .ifPresent(path -> session.locationAudioPath = path);
        } else {
            throw new IllegalArgumentException("step invalide: " + step + " (attendu: description ou location)");
        }

        // Lancer le traitement si les 2 étapes sont complètes
        CompletableFuture.runAsync(() -> maybeProcessSession(resolvedSessionId), executor);

        return resolvedSessionId;
    }

    /**
     * Tentative d'écriture sur disque (optionnelle — ne fait pas échouer l'upload)
     */
    private java.util.Optional<String> tryWriteToDisk(byte[] audioBytes, String filename) {
        try {
            File dir = new File(VOICE_UPLOAD_DIR);
            if (!dir.exists()) dir.mkdirs();
            String filepath = VOICE_UPLOAD_DIR + File.separator + filename;
            Files.write(Paths.get(filepath), audioBytes);
            return java.util.Optional.of(filepath);
        } catch (Exception e) {
            log.debug("[VOICE] Écriture disque ignorée (stockage mémoire utilisé): {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Traite un signalement Jami (messages texte via le bot Node.js).
     * La description et la localisation sont directement en texte (pas d'audio).
     */
    public JamiSignalementResponse processJamiMessage(JamiMessageRequest request) {
        final String sessionId = isBlank(request.getSessionId())
            ? "jami-" + UUID.randomUUID().toString()
            : request.getSessionId();

        log.info("[JAMI] Traitement message: session={}, from={}", sessionId, request.getContactUri());

        // Structuration avec IA (ou fallback)
        Map<String, Object> structured = structureWithAI(
            request.getDescription(),
            request.getLocation(),
            "jami"
        );

        // Enrichir avec les données Jami
        if (!structured.containsKey("adresse") || structured.get("adresse") == null) {
            structured.put("adresse", request.getLocation());
        }

        // Créer un objet session temporaire
        HybridVoiceSession tempSession = new HybridVoiceSession(sessionId, "jami");
        tempSession.descriptionTranscription = request.getDescription();
        tempSession.locationTranscription = request.getLocation();

        // Construire et sauvegarder le signalement
        SignalementRequest signalementRequest = buildSignalementRequest(structured, tempSession);
        String citoyenId = "jami:" + (request.getContactUri() != null
            ? request.getContactUri().replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(20, request.getContactUri().length()))
            : "anonymous");

        Signalement saved = signalementService.create(signalementRequest, citoyenId);

        log.info("[JAMI] Signalement #{} créé depuis Jami pour {}", saved.getId(), request.getContactUri());

        return JamiSignalementResponse.builder()
            .signalementId(saved.getId())
            .type(saved.getType().name())
            .description(saved.getDescription())
            .adresse(saved.getAdresse())
            .priorite(saved.getPrioriteCitoyen().name())
            .statut(saved.getStatut().name())
            .sessionId(sessionId)
            .build();
    }

    /**
     * Récupère l'état d'une session (debug/admin)
     */
    public HybridVoiceSessionResponse getSession(String sessionId) {
        HybridVoiceSession session = sessions.get(sessionId);
        if (session == null) {
            return HybridVoiceSessionResponse.builder()
                .sessionId(sessionId)
                .errorMessage("Session introuvable")
                .completed(false)
                .build();
        }

        return HybridVoiceSessionResponse.builder()
            .sessionId(session.sessionId)
            .source(session.source)
            .descriptionReceived(session.descriptionReceived)
            .locationReceived(session.locationReceived)
            .descriptionTranscription(session.descriptionTranscription)
            .locationTranscription(session.locationTranscription)
            .structuredData(session.structuredData)
            .signalementId(session.signalementId)
            .completed(session.completed)
            .errorMessage(session.errorMessage)
            .createdAt(session.createdAt)
            .build();
    }

    /**
     * Récupère l'audio enregistré (pour relecture admin)
     * Priorité : mémoire > disque
     */
    public byte[] getAudio(String sessionId, String step) throws IOException {
        HybridVoiceSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session " + sessionId + " introuvable");
        }

        // Depuis mémoire (prioritaire)
        if ("description".equalsIgnoreCase(step) && session.descriptionAudioBytes != null) {
            return session.descriptionAudioBytes;
        }
        if ("location".equalsIgnoreCase(step) && session.locationAudioBytes != null) {
            return session.locationAudioBytes;
        }

        // Fallback : depuis disque
        String filepath = "description".equalsIgnoreCase(step)
            ? session.descriptionAudioPath
            : session.locationAudioPath;

        if (isBlank(filepath)) {
            throw new IllegalArgumentException("Audio " + step + " non disponible");
        }
        File file = new File(filepath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Fichier audio non trouvé: " + filepath);
        }
        return Files.readAllBytes(file.toPath());
    }

    /**
     * Traitement principal: transcription + structuration + création signalement
     */
    private void maybeProcessSession(String sessionId) {
        HybridVoiceSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }

        synchronized (session) {
            if (session.processingStarted || session.completed) {
                return;
            }
            if (!session.descriptionReceived || !session.locationReceived) {
                return;
            }
            session.processingStarted = true;
        }

        try {
            // Étape 1: Transcription (depuis mémoire en priorité, disque en fallback)
            byte[] descBytes = session.descriptionAudioBytes != null
                ? session.descriptionAudioBytes
                : (session.descriptionAudioPath != null ? readFile(session.descriptionAudioPath) : new byte[0]);
            byte[] locBytes = session.locationAudioBytes != null
                ? session.locationAudioBytes
                : (session.locationAudioPath != null ? readFile(session.locationAudioPath) : new byte[0]);

            String descriptionText = transcribeAudioBytes(descBytes, sessionId + "-description");
            String locationText = transcribeAudioBytes(locBytes, sessionId + "-location");

            // Nettoyage + fuzzy-match post-Whisper : corrige "nid de poulet" → "nid-de-poule",
            // "la Marta" → "La Marsa", etc. contre un dictionnaire urbain + toponymes tunisiens.
            session.descriptionTranscription = voiceTextCorrector.correct(
                cleanTranscript(stripTtsEcho(descriptionText, "description")));
            session.locationTranscription    = voiceTextCorrector.correct(
                cleanTranscript(stripTtsEcho(locationText, "location")));

            log.info("[VOICE] Transcriptions complétées pour {}: desc='{}', loc='{}'",
                sessionId, session.descriptionTranscription, session.locationTranscription);

            // Guard: si la transcription a échoué (placeholder entre crochets), abandonner
            boolean descFailed = session.descriptionTranscription != null
                && session.descriptionTranscription.startsWith("[");
            boolean locFailed  = session.locationTranscription   != null
                && session.locationTranscription.startsWith("[");

            if (descFailed || locFailed) {
                session.errorMessage = "Transcription échouée — signalement non créé."
                    + (descFailed ? " Description: " + session.descriptionTranscription + "." : "")
                    + (locFailed  ? " Localisation: " + session.locationTranscription  + "." : "");
                log.warn("[VOICE] Session {} abandonnée — transcription invalide (desc='{}', loc='{}')",
                    sessionId, session.descriptionTranscription, session.locationTranscription);
                return; // ← NE PAS créer le signalement
            }

            // Étape 2: Structuration avec Ollama/IA
            Map<String, Object> structured = structureWithAI(
                session.descriptionTranscription,
                session.locationTranscription,
                session.source
            );
            session.structuredData = structured;

            // Étape 3: Création signalement
            SignalementRequest request = buildSignalementRequest(structured, session);
            // Utiliser l'ID du citoyen connecté, sinon "voice-bot" comme fallback
            String citoyenId = !isBlank(session.userId) ? session.userId : "voice-bot";
            Signalement saved = signalementService.create(request, citoyenId);

            session.signalementId = saved.getId();
            session.completed = true;

            log.info("[VOICE] Signalement vocal créé #{} - source:{}, session:{}",
                saved.getId(), session.source, sessionId);

        } catch (Exception e) {
            session.errorMessage = e.getMessage();
            log.error("[VOICE] Erreur traitement session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Transcription avec Whisper (depuis bytes en mémoire)
     */
    private String transcribeAudioBytes(byte[] audioBytes, String filename) {
        if (audioBytes == null || audioBytes.length == 0) {
            log.warn("[VOICE] Audio vide pour {}", filename);
            return "[Audio non capturé]";
        }
        if (isBlank(properties.getWhisperUrl())) {
            log.info("[VOICE] Whisper non configuré — transcription ignorée pour {}", filename);
            return "[Transcription non disponible - Whisper pas configuré]";
        }
        try {
            return callWhisper(audioBytes, filename + ".webm");
        } catch (Exception e) {
            log.warn("[VOICE] Erreur Whisper pour {}: {}", filename, e.getMessage());
            return "[Transcription échouée]";
        }
    }

    /**
     * Appel à Whisper (OpenAI compatible)
     */
    @SuppressWarnings("unchecked")
    private String callWhisper(byte[] audio, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (!isBlank(properties.getWhisperApiKey())) {
            headers.setBearerAuth(properties.getWhisperApiKey());
        }

        ByteArrayResource resource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        body.add("model", properties.getWhisperModel());
        body.add("language", properties.getWhisperLanguage());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                properties.getWhisperUrl(),
                requestEntity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object text = response.getBody().get("text");
                if (text != null && !text.toString().isBlank()) {
                    return text.toString().trim();
                }
            }
        } catch (Exception e) {
            log.warn("[VOICE] Erreur Whisper: {}", e.getMessage());
        }

        return "[Transcription échouée]";
    }

    /**
     * Structuration avec IA locale (Ollama) ou API
     */
    private Map<String, Object> structureWithAI(String description, String location, String source) {
        try {
            // Appel Ollama local
            String prompt = buildOllamaPrompt(description, location, source);
            String jsonResponse = callOllama(prompt);

            Map<String, Object> parsed = objectMapper.readValue(jsonResponse, Map.class);
            log.debug("[VOICE] Structuration IA réussie: {}", parsed);
            return parsed;

        } catch (Exception e) {
            log.warn("[VOICE] Structuration IA échouée: {}, fallback manuel", e.getMessage());
            // Fallback: structuration manuelle basée sur mots-clés
            return buildFallbackStructure(description, location, source);
        }
    }

    /**
     * Appel à Ollama (llama2, mistral, etc.)
     */
    private String callOllama(String prompt) throws Exception {
        String ollamaUrl = "http://localhost:11434/api/generate";

        Map<String, Object> body = Map.of(
            "model", "mistral",
            "prompt", prompt,
            "stream", false
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body);
        ResponseEntity<String> response = restTemplate.postForEntity(ollamaUrl, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Ollama erreur: " + response.getStatusCode());
        }

        // Extraire le JSON depuis la réponse Ollama
        Map<String, Object> resp = objectMapper.readValue(response.getBody(), Map.class);
        String responseText = (String) resp.get("response");

        // Parser le JSON depuis la réponse
        int jsonStart = responseText.indexOf('{');
        int jsonEnd = responseText.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return responseText.substring(jsonStart, jsonEnd + 1);
        }

        throw new RuntimeException("Ollama: JSON invalide dans la réponse");
    }

    /**
     * Construit le prompt pour Ollama
     */
    private String buildOllamaPrompt(String description, String location, String source) {
        return """
Tu es assistant municipal. À partir de ces 2 transcriptions vocales, extrait les informations structurées.

Étape 1 (description du problème): "%s"
Étape 2 (adresse complète): "%s"
Source: %s

Retourne UNIQUEMENT ce JSON (pas de texte autour):
{
  "type": "VOIRIE|ECLAIRAGE|PLOMBERIE|PROPRETE|ASSAINISSEMENT|ESPACES_VERTS|AUTRE",
  "description": "description concise du problème",
  "priorite": "URGENTE|HAUTE|MOYENNE|FAIBLE",
  "adresse": "adresse ou null",
  "source": "%s",
  "latitude": null,
  "longitude": null
}
            """.formatted(escapeJson(description), escapeJson(location), source, source);
    }

    /**
     * Structuration de secours (sans IA)
     */
    private Map<String, Object> buildFallbackStructure(String description, String location, String source) {
        String type = inferType(description);
        String priorite = inferPriority(description);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("type", type);
        result.put("description", description.length() > 10 ? description : description + " (à vérifier)");
        result.put("priorite", priorite);
        result.put("adresse", isBlank(location) ? "Localisation à confirmer" : location);
        result.put("source", source);
        result.put("latitude", properties.getDefaultLatitude());
        result.put("longitude", properties.getDefaultLongitude());
        return result;
    }

    /**
     * Inférence du type de signalement
     */
    private String inferType(String text) {
        String normalized = normalize(text);

        if (normalized.contains("lampadaire") || normalized.contains("eclairage") || normalized.contains("lumiere")) {
            return "ECLAIRAGE";
        }
        if (normalized.contains("fuite") || normalized.contains("eau") || normalized.contains("plomb")) {
            return "PLOMBERIE";
        }
        if (normalized.contains("dechet") || normalized.contains("ordure") || normalized.contains("poubelle")) {
            return "PROPRETE";
        }
        if (normalized.contains("egout") || normalized.contains("caniveau") || normalized.contains("assainissement")) {
            return "ASSAINISSEMENT";
        }
        if (normalized.contains("arbre") || normalized.contains("espace vert") || normalized.contains("jardin")) {
            return "ESPACES_VERTS";
        }
        if (normalized.contains("trou") || normalized.contains("chaussee") || normalized.contains("route")) {
            return "VOIRIE";
        }
        return "AUTRE";
    }

    /**
     * Inférence de la priorité
     */
    private String inferPriority(String text) {
        String normalized = normalize(text);

        if (normalized.contains("urgent") || normalized.contains("danger") || normalized.contains("risque")) {
            return "URGENTE";
        }
        if (normalized.contains("important") || normalized.contains("grave")) {
            return "HAUTE";
        }
        if (normalized.contains("normal") || normalized.contains("routine")) {
            return "MOYENNE";
        }
        return "FAIBLE";
    }

    /**
     * Construit la requête de création de signalement
     */
    private SignalementRequest buildSignalementRequest(Map<String, Object> structured, HybridVoiceSession session) {
        SignalementRequest request = new SignalementRequest();

        String type      = (String) structured.getOrDefault("type", "AUTRE");
        String priorite  = (String) structured.getOrDefault("priorite", "MOYENNE");
        String description = (String) structured.getOrDefault("description", session.descriptionTranscription);
        String adresse   = (String) structured.getOrDefault("adresse",
            !isBlank(session.locationTranscription) ? session.locationTranscription : "Localisation à confirmer");

        request.setType(mapTypeEnum(type));
        request.setPrioriteCitoyen(mapPrioriteEnum(priorite));
        request.setDescription(!isBlank(description) && description.length() >= 10
            ? description : (description + " (vocal)").trim());
        request.setAdresse(adresse);

        // Géocodage via Nominatim (OpenStreetMap) — gratuit, sans clé API
        double[] coords = geocodeAddress(adresse);
        request.setLatitude(coords[0]);
        request.setLongitude(coords[1]);
        request.setEstAnonyme(false);
        request.setImageBase64(null);
        request.setVoiceSessionId(session.sessionId);  // pour permettre la réécoute admin

        return request;
    }

    /**
     * Convertit une adresse textuelle en coordonnées GPS via Nominatim (OpenStreetMap).
     * Fallback sur les coordonnées par défaut si le géocodage échoue.
     */
    // ── Coordonnées GPS de référence pour lieux/villes tunisiens ─────────────
    // Utilisé quand Nominatim échoue (OSM Tunisie incomplet pour les bâtiments).
    // Format : "mot-clé-normalisé" → [lat, lon]
    //
    // IMPORTANT : les clés sont triées par longueur DÉCROISSANTE au moment du
    // lookup pour que "tunis el manar" matche avant "tunis", ou que "el menzah 6"
    // matche avant "el menzah".
    private static final java.util.Map<String, double[]> KNOWN_PLACES;
    /** Liste triée par longueur décroissante pour un matching "plus-spécifique d'abord". */
    private static final java.util.List<java.util.Map.Entry<String, double[]>> KNOWN_PLACES_SORTED;
    static {
        java.util.Map<String, double[]> m = new java.util.LinkedHashMap<>();
        // ─ Universités & grandes écoles ──────────────────────────────────────
        m.put("esprit",                   new double[]{36.8614, 10.1955}); // Univ. Esprit, Ariana
        m.put("tunis el manar",           new double[]{36.8340, 10.1647});
        m.put("universite el manar",      new double[]{36.8340, 10.1647});
        m.put("universite centrale",      new double[]{36.8190, 10.1660});
        m.put("enit",                     new double[]{36.8360, 10.1640});
        m.put("iset rades",               new double[]{36.7680, 10.2760});
        m.put("iset charguia",            new double[]{36.8527, 10.2257});
        m.put("iset",                     new double[]{36.8190, 10.1680});
        m.put("ihec",                     new double[]{36.8776, 10.3240}); // IHEC Carthage
        m.put("fsegt",                    new double[]{36.8340, 10.1647});
        m.put("fst",                      new double[]{36.8340, 10.1647}); // Fac Sciences Tunis
        m.put("insat",                    new double[]{36.8430, 10.1800});
        m.put("ensi",                     new double[]{36.8093, 10.0970}); // Manouba
        m.put("supcom",                   new double[]{36.8614, 10.1955}); // Sup'Com Ariana
        m.put("isg",                      new double[]{36.8190, 10.1660});
        m.put("isae",                     new double[]{36.8625, 10.1956}); // Aéronautique Ariana
        m.put("mediterranean school",     new double[]{36.8614, 10.1955});
        m.put("universite de carthage",   new double[]{36.9059, 10.3064});
        m.put("carthage",                 new double[]{36.9059, 10.3064});
        m.put("sfax universite",          new double[]{34.7424, 10.7619});
        m.put("universite sousse",        new double[]{35.8245, 10.6346});
        m.put("universite monastir",      new double[]{35.7779, 10.8262});

        // ─ Quartiers du Grand Tunis ──────────────────────────────────────────
        m.put("el menzah 1",              new double[]{36.8380, 10.1710});
        m.put("el menzah 2",              new double[]{36.8440, 10.1750});
        m.put("el menzah 3",              new double[]{36.8490, 10.1790});
        m.put("el menzah 4",              new double[]{36.8550, 10.1810});
        m.put("el menzah 5",              new double[]{36.8520, 10.1720});
        m.put("el menzah 6",              new double[]{36.8580, 10.1870});
        m.put("el menzah 7",              new double[]{36.8610, 10.1900});
        m.put("el menzah 8",              new double[]{36.8600, 10.1830});
        m.put("el menzah 9",              new double[]{36.8670, 10.1870});
        m.put("el menzah",                new double[]{36.8490, 10.1790});
        m.put("menzah 6",                 new double[]{36.8580, 10.1870});
        m.put("menzah 9",                 new double[]{36.8670, 10.1870});
        m.put("menzah",                   new double[]{36.8490, 10.1790});
        m.put("el manar 1",               new double[]{36.8360, 10.1580});
        m.put("el manar 2",               new double[]{36.8410, 10.1530});
        m.put("el manar",                 new double[]{36.8385, 10.1555});
        m.put("hay el manar",             new double[]{36.8385, 10.1555});
        m.put("cite ennasr 1",            new double[]{36.8506, 10.1670});
        m.put("cite ennasr 2",            new double[]{36.8566, 10.1700});
        m.put("cite ennasr",              new double[]{36.8533, 10.1686});
        m.put("hay ennasr",               new double[]{36.8533, 10.1686});
        m.put("ennasr 1",                 new double[]{36.8506, 10.1670});
        m.put("ennasr 2",                 new double[]{36.8566, 10.1700});
        m.put("ennasr",                   new double[]{36.8533, 10.1686});
        m.put("cite olympique",           new double[]{36.8439, 10.1870});
        m.put("cite ghazela",             new double[]{36.8880, 10.1913});
        m.put("cite ghazala",             new double[]{36.8880, 10.1913});
        m.put("cite ettadhamen",          new double[]{36.8380, 10.1115});
        m.put("cite el khadra",           new double[]{36.8135, 10.1865});
        m.put("cite jardins",             new double[]{36.8390, 10.1880});
        m.put("cite mahrajene",           new double[]{36.8210, 10.1810});
        m.put("cite ibn khaldoun",        new double[]{36.8110, 10.1565});
        m.put("ibn khaldoun",             new double[]{36.8110, 10.1565});
        m.put("el omrane superieur",      new double[]{36.8240, 10.1560});
        m.put("el omrane",                new double[]{36.8180, 10.1550});
        m.put("omrane",                   new double[]{36.8180, 10.1550});
        m.put("bab souika",               new double[]{36.8115, 10.1735});
        m.put("bab el khadra",            new double[]{36.8160, 10.1735});
        m.put("bab bhar",                 new double[]{36.8002, 10.1800});
        m.put("bab jedid",                new double[]{36.7950, 10.1690});
        m.put("bab saadoun",              new double[]{36.8145, 10.1580});
        m.put("bab alioua",               new double[]{36.7900, 10.1770});
        m.put("montplaisir",              new double[]{36.8170, 10.1910});
        m.put("mutuelleville",            new double[]{36.8250, 10.1820});
        m.put("lafayette",                new double[]{36.8050, 10.1850});
        m.put("passage",                  new double[]{36.8010, 10.1820});
        m.put("belvedere",                new double[]{36.8250, 10.1680});
        m.put("le bardo",                 new double[]{36.8093, 10.1370});
        m.put("bardo",                    new double[]{36.8093, 10.1370});
        m.put("medina tunis",             new double[]{36.7980, 10.1710});
        m.put("medina",                   new double[]{36.7980, 10.1710});
        m.put("kasbah",                   new double[]{36.7981, 10.1683});
        m.put("jardin thameur",           new double[]{36.8070, 10.1820});
        m.put("avenue habib bourguiba",   new double[]{36.8010, 10.1820});
        m.put("place barcelone",          new double[]{36.7979, 10.1820});
        m.put("place de la republique",   new double[]{36.7990, 10.1830});
        m.put("place 14 janvier",         new double[]{36.8000, 10.1840});
        m.put("place africa",             new double[]{36.8070, 10.1865});
        m.put("gare tunis",               new double[]{36.7970, 10.1825});
        m.put("gare de tunis",            new double[]{36.7970, 10.1825});
        m.put("aeroport tunis",           new double[]{36.8499, 10.2271});
        m.put("aeroport carthage",        new double[]{36.8499, 10.2271});

        // ─ Ariana ────────────────────────────────────────────────────────────
        m.put("riadh el andalous",        new double[]{36.8748, 10.1765});
        m.put("cite jinene el riadh",     new double[]{36.8770, 10.1790});
        m.put("borj louzir",              new double[]{36.8765, 10.1920});
        m.put("raoued plage",             new double[]{36.9120, 10.2310});
        m.put("raoued",                   new double[]{36.8890, 10.2004});
        m.put("soukra",                   new double[]{36.8817, 10.2296});
        m.put("la soukra",                new double[]{36.8817, 10.2296});
        m.put("ghazala",                  new double[]{36.8880, 10.1913});
        m.put("ariana soghra",            new double[]{36.8560, 10.1930});
        m.put("ariana ville",             new double[]{36.8625, 10.1956});
        m.put("kram ouest",               new double[]{36.8500, 10.3080});
        m.put("le kram",                  new double[]{36.8439, 10.3094});
        m.put("kram",                     new double[]{36.8439, 10.3094});
        m.put("sidi bou said",             new double[]{36.8706, 10.3481});
        m.put("la goulette",              new double[]{36.8193, 10.3059});
        m.put("la goulette ville",        new double[]{36.8193, 10.3059});
        m.put("goulette",                 new double[]{36.8193, 10.3059});

        // ─ Charguia / Aouina ─────────────────────────────────────────────────
        m.put("charguia 1",               new double[]{36.8527, 10.2257});
        m.put("charguia 2",               new double[]{36.8627, 10.2380});
        m.put("charguia",                 new double[]{36.8527, 10.2257});
        m.put("aouina",                   new double[]{36.8459, 10.2450});
        m.put("l aouina",                 new double[]{36.8459, 10.2450});

        // ─ Marsa & littoral nord ─────────────────────────────────────────────
        m.put("la marsa plage",           new double[]{36.8880, 10.3220});
        m.put("marsa ville",              new double[]{36.8776, 10.3240});
        m.put("marsa corniche",           new double[]{36.8830, 10.3280});
        m.put("la marsa",                 new double[]{36.8776, 10.3240});
        m.put("marsa",                    new double[]{36.8776, 10.3240});
        m.put("gammarth",                 new double[]{36.9207, 10.2868});
        m.put("la corniche",              new double[]{36.8830, 10.3280});

        // ─ Ben Arous ─────────────────────────────────────────────────────────
        m.put("el mourouj 1",             new double[]{36.7320, 10.2320});
        m.put("el mourouj 2",             new double[]{36.7340, 10.2380});
        m.put("el mourouj 3",             new double[]{36.7360, 10.2440});
        m.put("el mourouj 4",             new double[]{36.7380, 10.2500});
        m.put("el mourouj 5",             new double[]{36.7400, 10.2560});
        m.put("el mourouj 6",             new double[]{36.7420, 10.2620});
        m.put("mourouj",                  new double[]{36.7373, 10.2422});
        m.put("el mourouj",               new double[]{36.7373, 10.2422});
        m.put("ezzahra",                  new double[]{36.7453, 10.3016});
        m.put("hammam lif",               new double[]{36.7221, 10.3396});
        m.put("hammam chatt",              new double[]{36.7071, 10.3460});
        m.put("borj cedria",              new double[]{36.7158, 10.4157});
        m.put("rades ville",              new double[]{36.7680, 10.2760});
        m.put("rades meliane",            new double[]{36.7580, 10.2630});
        m.put("rades",                    new double[]{36.7680, 10.2760});
        m.put("megrine",                  new double[]{36.7610, 10.2290});
        m.put("mohamedia",                new double[]{36.6880, 10.1460});
        m.put("fouchana",                 new double[]{36.6933, 10.1650});
        m.put("mornaguia",                new double[]{36.7556, 10.0167});
        m.put("mornag",                   new double[]{36.6770, 10.2970});

        // ─ Manouba ───────────────────────────────────────────────────────────
        m.put("manouba ville",            new double[]{36.8093, 10.0970});
        m.put("oued ellil",               new double[]{36.8340, 10.0180});
        m.put("denden",                   new double[]{36.8250, 10.0740});

        // ─ Grandes entreprises / zones industrielles / loisirs ───────────────
        m.put("berges du lac 1",          new double[]{36.8361, 10.2337});
        m.put("berges du lac 2",          new double[]{36.8495, 10.2586});
        m.put("berges du lac",            new double[]{36.8361, 10.2337});
        m.put("lac 1",                    new double[]{36.8361, 10.2337});
        m.put("lac 2",                    new double[]{36.8495, 10.2586});
        m.put("centre urbain nord",       new double[]{36.8430, 10.1800});
        m.put("zone industrielle",        new double[]{36.8527, 10.2257});

        // ─ Centres commerciaux / lieux publics ───────────────────────────────
        m.put("mall of tunis",            new double[]{36.8130, 10.1710});
        m.put("tunisia mall",             new double[]{36.8130, 10.1710});
        m.put("city center",              new double[]{36.8120, 10.1720});
        m.put("carrefour la marsa",       new double[]{36.8800, 10.3220});
        m.put("carrefour menzah",         new double[]{36.8490, 10.1790});
        m.put("carrefour",                new double[]{36.8120, 10.1720});
        m.put("geant",                    new double[]{36.8361, 10.2337});
        m.put("azur city",                new double[]{36.8810, 10.3200});
        m.put("mall of sousse",           new double[]{35.8310, 10.6220});
        m.put("mall of sfax",             new double[]{34.7396, 10.7608});

        // ─ Villes / gouvernorats (fallback grande maille) ────────────────────
        m.put("tunis centre",             new double[]{36.8010, 10.1820});
        m.put("tunis",                    new double[]{36.8190, 10.1660});
        m.put("ariana",                   new double[]{36.8625, 10.1956});
        m.put("ben arous",                new double[]{36.7531, 10.2281});
        m.put("manouba",                  new double[]{36.8093, 10.0970});
        m.put("hammamet",                 new double[]{36.4000, 10.6167});
        m.put("nabeul",                   new double[]{36.4513, 10.7357});
        m.put("zaghouan",                 new double[]{36.4029, 10.1426});
        m.put("bizerte",                  new double[]{37.2744, 9.8738});
        m.put("beja",                     new double[]{36.7246, 9.1829});
        m.put("jendouba",                 new double[]{36.5011, 8.7757});
        m.put("el kef",                   new double[]{36.1674, 8.7146});
        m.put("siliana",                  new double[]{36.0849, 9.3719});
        m.put("sousse",                   new double[]{35.8245, 10.6346});
        m.put("monastir",                 new double[]{35.7779, 10.8262});
        m.put("mahdia",                   new double[]{35.5047, 11.0622});
        m.put("djerba",                   new double[]{33.8076, 10.8451});
        m.put("zarzis",                   new double[]{33.5042, 11.1120});
        m.put("tabarka",                  new double[]{36.9544, 8.7581});
        m.put("sfax",                     new double[]{34.7424, 10.7619});
        m.put("kairouan",                 new double[]{35.6712, 10.1005});
        m.put("kasserine",                new double[]{35.1676, 8.8365});
        m.put("sidi bouzid",              new double[]{35.0382, 9.4849});
        m.put("gabes",                    new double[]{33.8881, 10.0975});
        m.put("medenine",                 new double[]{33.3547, 10.5053});
        m.put("tataouine",                new double[]{32.9211, 10.4508});
        m.put("gafsa",                    new double[]{34.4250, 8.7842});
        m.put("tozeur",                   new double[]{33.9197, 8.1335});
        m.put("kebili",                   new double[]{33.7046, 8.9693});
        KNOWN_PLACES = java.util.Collections.unmodifiableMap(m);

        // Pré-calculer la liste triée par longueur DÉCROISSANTE :
        // "tunis el manar" (14) matchera AVANT "tunis" (5).
        java.util.List<java.util.Map.Entry<String, double[]>> sorted =
            new java.util.ArrayList<>(m.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        KNOWN_PLACES_SORTED = java.util.Collections.unmodifiableList(sorted);
    }

    /**
     * Cherche dans le dictionnaire des lieux connus si l'adresse contient un mot-clé reconnu.
     * Itère par longueur DÉCROISSANTE pour matcher le plus spécifique d'abord :
     *   "près de Tunis El Manar" → matche "tunis el manar" AVANT "tunis".
     * @return coordonnées GPS ou null
     */
    private double[] lookupKnownPlace(String text) {
        if (isBlank(text)) return null;
        String norm = normalize(text);
        for (java.util.Map.Entry<String, double[]> entry : KNOWN_PLACES_SORTED) {
            String key = entry.getKey();
            // Matching mot-entier : évite que "kram" matche dans "réclame" ou
            // que "tunis" matche dans "tunisie". Exige une frontière non-alphanumérique
            // (début/fin de chaîne ou espace/ponctuation) autour du mot-clé.
            if (containsAsWord(norm, key)) {
                log.info("[VOICE][GEO] Lieu connu détecté '{}' dans '{}' → ({}, {})",
                    key, text, entry.getValue()[0], entry.getValue()[1]);
                return entry.getValue();
            }
        }
        return null;
    }

    /** Vérifie que {@code key} apparaît dans {@code text} comme mot(s) entier(s). */
    private static boolean containsAsWord(String text, String key) {
        int idx = 0;
        while ((idx = text.indexOf(key, idx)) != -1) {
            boolean leftOk  = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            int endIdx = idx + key.length();
            boolean rightOk = endIdx == text.length() || !Character.isLetterOrDigit(text.charAt(endIdx));
            if (leftOk && rightOk) return true;
            idx += key.length();
        }
        return false;
    }

    /**
     * Géocodage avec stratégies multiples :
     * 1. Adresse nettoyée + restriction Tunisie (countrycodes=tn)
     * 2. Adresse nettoyée sans restriction (au cas où countrycodes filtre trop)
     * 3. Seulement la ville/gouvernorat détectée dans le texte
     */
    private double[] geocodeAddress(String adresse) {
        if (isBlank(adresse) || adresse.contains("confirmer") || adresse.contains("non disponible")
                || adresse.startsWith("[")) {
            return new double[]{properties.getDefaultLatitude(), properties.getDefaultLongitude()};
        }

        // ── Stratégie 0 : dictionnaire sur adresse brute (avant tout nettoyage) ──
        // Priorité absolue — ne dépend pas du nettoyage du texte.
        double[] result = lookupKnownPlace(adresse);
        if (result != null) return result;

        // Étape 1 : nettoyer le texte parlé (enlever mots parasites)
        String cleaned = cleanSpokenAddress(adresse);
        log.info("[VOICE][GEO] Adresse brute: '{}' → nettoyée: '{}'", adresse, cleaned);

        // ── Stratégie 0b : dictionnaire sur adresse nettoyée ──
        result = lookupKnownPlace(cleaned);
        if (result != null) return result;

        // ── Stratégie 1 : Nominatim avec adresse complète nettoyée ──
        result = tryNominatim(cleaned, true);
        if (result != null) return result;

        // ── Stratégie 2 : Nominatim sans restriction de zone ──
        result = tryNominatim(cleaned, false);
        if (result != null) return result;

        // ── Stratégie 3 : extraire ville/gouvernorat + chercher dans le dictionnaire ──
        String cityOnly = extractCityName(cleaned);
        if (!isBlank(cityOnly)) {
            // D'abord dans le dictionnaire
            result = lookupKnownPlace(cityOnly);
            if (result != null) return result;

            // Sinon Nominatim avec juste la ville
            log.info("[VOICE][GEO] Tentative ville uniquement: '{}'", cityOnly);
            result = tryNominatim(cityOnly, true);
            if (result != null) return result;
        }

        // ── Stratégie 4 : Nominatim avec les 2-3 derniers mots (souvent le lieu propre)
        //    Ex : "à côté de la mosquée El Manar" → "El Manar"
        String tail = extractTrailingProperName(cleaned);
        if (!isBlank(tail) && !tail.equalsIgnoreCase(cleaned)) {
            log.info("[VOICE][GEO] Tentative proper-name: '{}'", tail);
            result = tryNominatim(tail, true);
            if (result != null) return result;
        }

        log.warn("[VOICE][GEO] Géocodage impossible pour '{}' → coordonnées par défaut", adresse);
        return new double[]{properties.getDefaultLatitude(), properties.getDefaultLongitude()};
    }

    /**
     * Récupère les 2-3 derniers mots capitalisés d'une adresse nettoyée.
     * Ex : "mosquée El Manar" → "El Manar" ; "rue Habib Bourguiba" → "Habib Bourguiba".
     */
    private String extractTrailingProperName(String cleaned) {
        if (isBlank(cleaned)) return "";
        String[] words = cleaned.split("\\s+");
        // Prendre au max les 3 derniers mots
        int start = Math.max(0, words.length - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < words.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
    }

    /**
     * Requête Nominatim avec gestion des erreurs et timeout.
     * Pas de countrycodes=tn car OSM Tunisie est incomplet — on valide les coords après.
     * @param query      adresse à chercher
     * @param addTunisia ajouter ", Tunisie" si absent
     * @return [lat, lon] ou null si non trouvé / hors Tunisie
     */
    @SuppressWarnings("unchecked")
    private double[] tryNominatim(String query, boolean addTunisia) {
        try {
            String fullQuery = query;
            if (addTunisia && !normalize(query).contains("tunisie") && !normalize(query).contains("tunis")) {
                fullQuery = query + ", Tunisie";
            }

            // viewbox = boîte Tunisie, bounded=0 = préférer la zone sans l'imposer strictement
            String url = "https://nominatim.openstreetmap.org/search?q="
                + java.net.URLEncoder.encode(fullQuery, "UTF-8")
                + "&format=json&limit=5&addressdetails=0"
                + "&viewbox=7.5,30.0,11.6,38.0&bounded=0";

            log.debug("[VOICE][GEO] Nominatim query: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "CityVoice/1.0 (contact@cityvoice.tn)");
            headers.set("Accept-Language", "fr,ar;q=0.9");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<java.util.List> response = geoRestTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, entity, java.util.List.class);

            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && !response.getBody().isEmpty()) {

                for (Object item : response.getBody()) {
                    Map<String, Object> r = (Map<String, Object>) item;
                    double lat = Double.parseDouble(r.get("lat").toString());
                    double lon = Double.parseDouble(r.get("lon").toString());

                    // Valider que les coordonnées tombent bien en Tunisie
                    if (lat >= 30.0 && lat <= 38.0 && lon >= 7.5 && lon <= 11.6) {
                        log.info("[VOICE][GEO] Trouvé '{}' → ({}, {}) — {}",
                            fullQuery, lat, lon, r.getOrDefault("display_name", "?"));
                        return new double[]{lat, lon};
                    }
                }
                log.debug("[VOICE][GEO] Résultats hors Tunisie pour '{}'", fullQuery);
            } else {
                log.debug("[VOICE][GEO] Aucun résultat Nominatim pour '{}'", fullQuery);
            }
        } catch (Exception e) {
            log.warn("[VOICE][GEO] Nominatim timeout/erreur pour '{}': {}", query, e.getMessage());
        }
        return null;
    }

    /**
     * Nettoie une adresse parlée en supprimant les mots parasites.
     * Ex: "je me trouve devant le marché central rue Habib Bourguiba à Sfax"
     *   → "marché central rue Habib Bourguiba Sfax"
     */
    private String cleanSpokenAddress(String text) {
        if (isBlank(text)) return text;

        String result = text.trim();

        // Supprimer les phrases d'introduction parlée
        result = result.replaceAll("(?i)^(je (me trouve|suis|suis situé(?:e)?)|c'est|il y a un problème|"
            + "ça se passe|le problème est|c'est à|ça se trouve|je parle de|je suis actuellement|"
            + "on est|on se trouve|nous sommes|je me situe|je me trouve actuellement|"
            + "actuellement à|actuellement je suis|ici à|ici c'est)\\s+", "");

        // Supprimer les prépositions de localisation (début OU interne en début après clean)
        result = result.replaceAll("(?i)^(devant|derrière|à côté de|à coté de|au niveau de|près de|"
            + "pres de|en face de|au coin de|à l'entrée de|à la sortie de|entre|juste devant|"
            + "juste à côté de|vers|autour de|au bord de|du côté de|à proximité de)\\s+", "");

        // Supprimer les articles parasites en début si suivis d'un lieu : "la mosquée X",
        // "l'université Y", "le lycée Z" → garde "X" / "Y" / "Z" (Nominatim est plus précis
        // sur le nom propre seul que sur "mosquée El Manar" qui trouve mal).
        result = result.replaceAll("(?i)^(la mosqu(?:é|e)e|le mosqu(?:é|e)e|l[' ]?université|"
            + "l[' ]?ecole|l[' ]?école|le lycée|le lycee|le collège|le college|"
            + "l[' ]?h[oô]pital|la pharmacie|la boulangerie|le supermarch[eé]|le march[eé]|"
            + "la station|la gare|le caf[eé]|la banque|la poste|le parc|le jardin|le stade)\\s+", "");

        // Supprimer tout ce qui suit une indication de signalement
        result = result.replaceAll("(?i)\\s+(pour (signaler|vous signaler|déclarer)|"
            + "où il y a|et je voudrais|je voudrais|il y a un(?:e)?|on a un(?:e)?|"
            + "pour un|pour une|concernant|par rapport à|merci d'avance|merci bien).*$", "");

        // Normaliser les espaces
        result = result.replaceAll("\\s+", " ").trim();

        // Capitaliser la première lettre
        if (!result.isEmpty()) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }

        return result;
    }

    /**
     * Extrait le nom de ville / gouvernorat tunisien dans un texte.
     * Utilisé en dernier recours si l'adresse complète n'est pas trouvée.
     */
    private String extractCityName(String text) {
        if (isBlank(text)) return "";

        // Villes, gouvernorats ET quartiers Grand Tunis — plus long en premier
        // pour matcher "El Menzah 6" avant "El Menzah", "Cité Ennasr" avant "Ennasr", etc.
        String[] cities = {
            // Quartiers Grand Tunis (matching fin)
            "El Menzah 9", "El Menzah 6", "El Menzah 1", "El Menzah",
            "El Manar 2", "El Manar 1", "El Manar",
            "Cité Ennasr 2", "Cité Ennasr 1", "Cité Ennasr", "Ennasr",
            "Berges du Lac 2", "Berges du Lac 1", "Berges du Lac",
            "El Mourouj 6", "El Mourouj", "Mourouj",
            "La Marsa", "La Goulette", "Le Kram", "Le Bardo",
            "Sidi Bou Said", "Gammarth", "Carthage",
            "Raoued", "Soukra", "Charguia", "Aouina",
            "Hammam-Lif", "Hammam Lif", "Hammam Chatt",
            "Ezzahra", "Rades", "Mégrine", "Megrine", "Mohamedia",
            "Montplaisir", "Mutuelleville", "Lafayette", "Belvédère",
            "Ibn Khaldoun", "El Omrane", "Bab Souika",
            "Cité Olympique", "Cité Ghazela", "Cité Ettadhamen",
            // Villes & gouvernorats
            "Tunis", "Sfax", "Sousse", "Kairouan", "Bizerte", "Gabès",
            "Ariana", "Gafsa", "Monastir", "Ben Arous", "Kasserine",
            "Médenine", "Nabeul", "Tataouine", "Béja", "Jendouba",
            "El Kef", "Mahdia", "Sidi Bouzid", "Tozeur", "Siliana",
            "Zaghouan", "Kebili", "Manouba",
            "Hammamet", "Djerba", "Zarzis", "Tabarka"
        };

        String normalized = normalize(text);
        for (String city : cities) {
            String nc = normalize(city);
            if (normalized.contains(nc)) {
                return city + ", Tunisie";
            }
        }
        return "";
    }

    /**
     * Mapping TypeSignalement
     */
    private TypeSignalement mapTypeEnum(String type) {
        return switch (type.toUpperCase()) {
            case "VOIRIE" -> TypeSignalement.TROU_CHAUSSEE;
            case "ECLAIRAGE" -> TypeSignalement.LAMPADAIRE_CASSE;
            case "PLOMBERIE" -> TypeSignalement.FUITE_EAU;
            case "PROPRETE" -> TypeSignalement.DECHETS_NON_COLLECTES;
            case "ASSAINISSEMENT" -> TypeSignalement.CANIVEAU_BOUCHE;
            case "ESPACES_VERTS" -> TypeSignalement.ESPACE_VERT_DEGRADE;
            default -> TypeSignalement.AUTRE;
        };
    }

    /**
     * Mapping Priorité
     */
    private Priorite mapPrioriteEnum(String priorite) {
        return switch (priorite.toUpperCase()) {
            case "URGENTE", "URGENT", "CRITIQUE" -> Priorite.URGENTE;
            case "HAUTE", "ELEVEE" -> Priorite.HAUTE;
            case "BASSE", "FAIBLE" -> Priorite.FAIBLE;
            default -> Priorite.MOYENNE;
        };
    }

    // ─── Utilitaires ───────────────────────────────────────────

    private byte[] decodeBase64Audio(String base64) {
        return java.util.Base64.getDecoder().decode(base64);
    }

    private byte[] readFile(String path) throws IOException {
        return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
    }

    @PostConstruct
    private void ensureUploadDir() {
        try {
            File dir = new File(VOICE_UPLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            log.info("[VOICE] Dossier audio: {}", VOICE_UPLOAD_DIR);
        } catch (Exception e) {
            log.warn("[VOICE] Impossible de créer le dossier audio: {} — le système vocal continuera sans persistance", e.getMessage());
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value
            .toLowerCase(Locale.ROOT)
            .replace('é', 'e').replace('è', 'e').replace('ê', 'e')
            .replace('à', 'a').replace('â', 'a')
            .replace('î', 'i').replace('ï', 'i')
            .replace('ô', 'o')
            .replace('ù', 'u').replace('û', 'u')
            .replace('ç', 'c')
            .replaceAll("\\s+", " ").trim();
    }

    /**
     * Supprime l'écho du TTS capturé par le microphone en début de transcription.
     *
     * Problème: le micro enregistre la fin de la question TTS avant que l'utilisateur parle.
     * Ex: "ou un point de repère à côté de l'université Esprit, bloc M"
     *      → le TTS a dit "...l'adresse ou un point de repère." et le micro l'a capturé.
     *
     * @param text  transcription brute de Whisper
     * @param step  "description" ou "location"
     * @return texte avec l'écho TTS supprimé
     */
    private String stripTtsEcho(String text, String step) {
        if (isBlank(text)) return text;

        String norm = normalize(text);

        if ("location".equalsIgnoreCase(step)) {
            // TTS: "Merci. Indiquez maintenant l'adresse ou un point de repère."
            // L'écho fréquent: "ou un point de repère" au début
            String[] locationEchoes = {
                "ou un point de repere",
                "un point de repere",
                "indiquez maintenant l adresse ou un point de repere",
                "indiquez l adresse ou un point de repere",
                "l adresse ou un point de repere",
                "adresse ou un point de repere",
                "merci indiquez maintenant",
                "indiquez maintenant",
            };
            for (String echo : locationEchoes) {
                int idx = norm.indexOf(echo);
                if (idx >= 0 && idx < 60) {
                    // Couper tout ce qui est avant ET l'écho lui-même
                    int cutEnd = idx + echo.length();
                    if (cutEnd < text.length()) {
                        String after = text.substring(cutEnd).replaceAll("^[\\s.,!?]+", "").trim();
                        if (!after.isBlank()) {
                            log.info("[VOICE] Écho TTS supprimé dans localisation: '{}' → '{}'", text, after);
                            return after;
                        }
                    }
                }
            }
        }

        if ("description".equalsIgnoreCase(step)) {
            // TTS: "Quel est votre signalement ?"
            String[] descEchoes = {
                "quel est votre signalement",
                "votre signalement",
            };
            for (String echo : descEchoes) {
                int idx = norm.indexOf(echo);
                if (idx >= 0 && idx < 50) {
                    int cutEnd = idx + echo.length();
                    if (cutEnd < text.length()) {
                        String after = text.substring(cutEnd).replaceAll("^[\\s.,!?]+", "").trim();
                        if (!after.isBlank()) {
                            log.info("[VOICE] Écho TTS supprimé dans description: '{}' → '{}'", text, after);
                            return after;
                        }
                    }
                }
            }
        }

        return text; // Pas d'écho détecté
    }

    private String cleanTranscript(String text) {
        if (text == null) return null;
        return text.replaceAll("\\s+", " ").trim();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Entité session vocale hybride
     */
    public static class HybridVoiceSession {
        public final String sessionId;
        public final String source; // "web" ou "jami"
        public final LocalDateTime createdAt;

        public volatile String userId;                 // ID du citoyen connecté
        public volatile byte[] descriptionAudioBytes;  // stockage mémoire (prioritaire)
        public volatile byte[] locationAudioBytes;     // stockage mémoire (prioritaire)
        public volatile String descriptionAudioPath;   // chemin disque (optionnel)
        public volatile String locationAudioPath;      // chemin disque (optionnel)
        public volatile boolean descriptionReceived;
        public volatile boolean locationReceived;
        public volatile String descriptionTranscription;
        public volatile String locationTranscription;
        public volatile Map<String, Object> structuredData;
        public volatile Long signalementId;
        public volatile boolean processingStarted;
        public volatile boolean completed;
        public volatile String errorMessage;

        public HybridVoiceSession(String sessionId, String source) {
            this.sessionId = sessionId;
            this.source = source;
            this.createdAt = LocalDateTime.now();
        }
    }
}
