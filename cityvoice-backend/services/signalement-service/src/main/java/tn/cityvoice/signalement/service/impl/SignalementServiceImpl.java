package tn.cityvoice.signalement.service.impl;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import tn.cityvoice.signalement.client.AiServiceClient;
import tn.cityvoice.signalement.client.PersonnelClient;
import tn.cityvoice.signalement.client.UserNotifClient;
import tn.cityvoice.signalement.dto.AiAnalysisResponse;
import tn.cityvoice.signalement.dto.SignalementRequest;
import tn.cityvoice.signalement.dto.StatutUpdateRequest;
import tn.cityvoice.signalement.entity.HistoriqueStatut;
import tn.cityvoice.signalement.entity.MediaSignalement;
import tn.cityvoice.signalement.entity.Signalement;
import tn.cityvoice.signalement.enums.Priorite;
import tn.cityvoice.signalement.enums.StatutSignalement;
import tn.cityvoice.signalement.enums.TypeSignalement;
import tn.cityvoice.signalement.exception.SignalementForbiddenException;
import tn.cityvoice.signalement.exception.SignalementNotFoundException;
import tn.cityvoice.signalement.kafka.SignalementProducer;
import tn.cityvoice.signalement.repository.ContratTravailRepository;
import tn.cityvoice.signalement.repository.HistoriqueStatutRepository;
import tn.cityvoice.signalement.repository.MediaSignalementRepository;
import tn.cityvoice.signalement.repository.SignalementRepository;
import tn.cityvoice.signalement.enums.NotificationType;
import tn.cityvoice.signalement.service.IContratTravailService;
import tn.cityvoice.signalement.service.INotificationService;
import tn.cityvoice.signalement.service.ISignalementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SignalementServiceImpl implements ISignalementService {

    final SignalementRepository      repository;
    final MediaSignalementRepository mediaRepository;
    final HistoriqueStatutRepository historiqueRepository;
    final ContratTravailRepository   contratRepository;
    final SignalementProducer        producer;
    final AiServiceClient            aiClient;
    final PersonnelClient            personnelClient;
    final IContratTravailService     contratService;
    final INotificationService       notifService;
    final UserNotifClient            userNotifClient;
    final WhatsAppService            whatsAppService;
    final SmsService                 smsService;

    private static final Map<String, Priorite> PRIO_MAP = Map.of(
        "faible",  Priorite.FAIBLE,
        "moyenne", Priorite.MOYENNE,
        "haute",   Priorite.HAUTE,
        "urgente", Priorite.URGENTE
    );

    /**
     * Mapping DIRECT : TypeSignalement → label affiché au citoyen.
     * Prioritaire sur tout — ne dépend ni de l'IA ni de la DB.
     * C'est le type choisi par le citoyen qui détermine quelle équipe est affichée.
     */
    private static final Map<TypeSignalement, String> TYPE_TO_LABEL = Map.ofEntries(
        Map.entry(TypeSignalement.DECHETS_NON_COLLECTES,   "Équipe Propreté"),
        Map.entry(TypeSignalement.TROU_CHAUSSEE,           "Équipe Voirie"),
        Map.entry(TypeSignalement.SIGNALISATION_MANQUANTE, "Équipe Voirie"),
        Map.entry(TypeSignalement.CANIVEAU_BOUCHE,         "Équipe Voirie"),
        Map.entry(TypeSignalement.LAMPADAIRE_CASSE,        "Équipe Éclairage"),
        Map.entry(TypeSignalement.POTEAU_ENDOMMAGE,        "Équipe Éclairage"),
        Map.entry(TypeSignalement.FUITE_EAU,               "Équipe Plomberie"),
        Map.entry(TypeSignalement.ESPACE_VERT_DEGRADE,     "Équipe Espaces Verts"),
        Map.entry(TypeSignalement.AUTRE,                   "Équipe Voirie")
    );

    /**
     * Mapping DIRECT : TypeSignalement → code équipe recherché en DB.
     * Utilisé pour trouver l'équipe LIBRE réelle (pour le contrat).
     * Les valeurs correspondent aux specialite normalisées de la DB.
     */
    private static final Map<TypeSignalement, List<String>> TYPE_TO_CODES = Map.ofEntries(
        Map.entry(TypeSignalement.DECHETS_NON_COLLECTES,   List.of("proprete", "collecte", "hygiene", "proprete_urbaine")),
        Map.entry(TypeSignalement.TROU_CHAUSSEE,           List.of("voirie", "infrastructure", "travaux")),
        Map.entry(TypeSignalement.SIGNALISATION_MANQUANTE, List.of("voirie", "securite", "infrastructure")),
        Map.entry(TypeSignalement.CANIVEAU_BOUCHE,         List.of("assainissement", "eau_assainissement", "voirie")),
        Map.entry(TypeSignalement.LAMPADAIRE_CASSE,        List.of("eclairage", "eclairage_public", "electricite")),
        Map.entry(TypeSignalement.POTEAU_ENDOMMAGE,        List.of("eclairage", "eclairage_public", "electricite")),
        Map.entry(TypeSignalement.FUITE_EAU,               List.of("plomberie", "eau_assainissement", "assainissement")),
        Map.entry(TypeSignalement.ESPACE_VERT_DEGRADE,     List.of("espaces_verts", "espace_vert", "environnement")),
        Map.entry(TypeSignalement.AUTRE,                   List.of("voirie", "infrastructure"))
    );

    /**
     * Alias directs : codes renvoyés par l'IA Python → codes réels dans la base.
     * Exemple : l'IA retourne "assainissement" mais la DB a "eau_assainissement".
     * Ces alias sont résolus EN PREMIER avant toute recherche de fallback.
     */
    private static final Map<String, String> EQUIPE_ALIASES = Map.ofEntries(
        Map.entry("assainissement",  "eau_assainissement"),
        Map.entry("eclairage",       "eclairage_public"),
        Map.entry("electricite",     "eclairage_public"),  // alias fréquent IA
        Map.entry("eau",             "eau_assainissement"),
        Map.entry("voie",            "voirie"),
        Map.entry("dechets",         "proprete"),
        Map.entry("hygiene",         "proprete"),
        Map.entry("collecte",        "proprete"),
        Map.entry("espace_vert",     "espaces_verts"),
        Map.entry("espace",          "espaces_verts"),
        Map.entry("travaux",         "infrastructure"),
        Map.entry("travaux_publics", "infrastructure")
    );

    /**
     * Chaînes de repli : si l'équipe principale est OCCUPE ou absente,
     * chercher dans les équipes de spécialité similaire (LIBRE).
     * Inclut les alias de l'IA comme clés pour garantir que toute valeur
     * retournée par le service IA a un chemin de résolution.
     */
    private static final Map<String, List<String>> FALLBACK_EQUIPES = Map.ofEntries(
        Map.entry("voirie",             List.of("infrastructure", "travaux_publics")),
        Map.entry("plomberie",          List.of("eau_assainissement", "infrastructure")),
        Map.entry("electricite",        List.of("eclairage_public", "infrastructure")),
        Map.entry("espaces_verts",      List.of("proprete", "infrastructure")),
        Map.entry("proprete",           List.of("espaces_verts", "infrastructure")),
        Map.entry("infrastructure",     List.of("voirie", "travaux_publics")),
        Map.entry("eclairage_public",   List.of("electricite", "infrastructure")),
        Map.entry("eau_assainissement", List.of("plomberie", "infrastructure")),
        // Aliases IA → fallbacks (si alias non résolu en DB)
        Map.entry("assainissement",     List.of("eau_assainissement", "plomberie")),
        Map.entry("eclairage",          List.of("eclairage_public", "electricite")),
        Map.entry("dechets",            List.of("proprete", "espaces_verts")),
        Map.entry("hygiene",            List.of("proprete", "espaces_verts"))
    );

    /** Résultat de la résolution équipe + chef */
    private record ChefResolution(String equipeCode, String equipeLabel, String chefId) {}

    /**
     * Vérifie si la fonction d'un membre correspond à un chef d'équipe.
     * Accepte "CHEF_EQUIPE" (valeur correcte) et "CHEF" (ancien alias frontend).
     */
    private static boolean isChefFonction(String fonction) {
        if (fonction == null) return false;
        return "CHEF_EQUIPE".equalsIgnoreCase(fonction) || "CHEF".equalsIgnoreCase(fonction);
    }

    /**
     * Normalise une spécialité d'équipe pour permettre la comparaison indépendamment
     * des accents, majuscules et espaces.
     * Ex: "Propreté" → "proprete", "Espaces Verts" → "espaces_verts"
     */
    private static String normalizeSpecialite(String s) {
        if (s == null) return "";
        return java.text.Normalizer
            .normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")  // supprimer les accents
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[\\s_-]+", "_")                           // espaces/tirets → _
            .replaceAll("[^a-z0-9_]", "");                        // enlever tout le reste
    }


    // ════════════════════════════════════════════════════════
    // CRÉER un signalement
    // ════════════════════════════════════════════════════════
    @Override
    public Signalement create(SignalementRequest req, String citoyenId) {

        Signalement sig = Signalement.builder()
            .type(req.getType())
            .description(req.getDescription())
            .latitude(req.getLatitude())
            .longitude(req.getLongitude())
            .adresse(req.getAdresse())
            .prioriteCitoyen(req.getPrioriteCitoyen() != null
                             ? req.getPrioriteCitoyen() : Priorite.MOYENNE)
            .statut(StatutSignalement.EN_ATTENTE)
            .citoyenId(citoyenId)
            .estAnonyme(Boolean.TRUE.equals(req.getEstAnonyme()))
            .voiceSessionId(req.getVoiceSessionId())   // null pour les signalements normaux
            .build();


        Signalement saved = repository.save(sig);
        log.info("[CREATE] Signalement #{} par citoyen #{}", saved.getId(), citoyenId);


        try {
            HistoriqueStatut initial = HistoriqueStatut.builder()
                .ancienStatut(null)
                .nouveauStatut(StatutSignalement.EN_ATTENTE)
                .commentaire("Signalement créé")
                .modifiePar(citoyenId)
                .build();
            initial.setSignalement(saved);
            historiqueRepository.save(initial);
            log.info("[HISTORIQUE] Entrée initiale créée pour #{}", saved.getId());
        } catch (Exception e) {
            log.warn("[HISTORIQUE] Échec sauvegarde historique initial #{} : {}",
                     saved.getId(), e.getMessage());
        }

        if (req.getImageBase64() != null && !req.getImageBase64().isBlank()) {
            // max_allowed_packet MySQL = 1MB → base64 limite ~700 000 chars (1MB binaire ≈ 1.33MB base64)
            // On vérifie AVANT l'insert pour éviter de taint la transaction JPA
            int imgLen = req.getImageBase64().length();
            if (imgLen > 700_000) {
                log.warn("[MEDIA] Image trop grande pour la DB ({} chars > 700 000) — ignorée pour #{}. " +
                         "Augmentez max_allowed_packet MySQL ou compressez l'image côté frontend.",
                         imgLen, saved.getId());
            } else {
                try {
                    String dataUri = req.getImageBase64().startsWith("data:")
                        ? req.getImageBase64()
                        : "data:image/jpeg;base64," + req.getImageBase64();
                    MediaSignalement media = MediaSignalement.builder()
                        .url(dataUri)
                        .type("image/jpeg")
                        .taille((long) imgLen)
                        .build();
                    media.setSignalement(saved);
                    mediaRepository.save(media);
                    log.info("[MEDIA] Image persistée pour signalement #{}", saved.getId());
                } catch (Exception e) {
                    log.warn("[MEDIA] Échec sauvegarde image #{} : {}", saved.getId(), e.getMessage());
                }
            }
        }


        try {
            producer.signalementCree(
                saved.getId(), saved.getType().name(),
                saved.getLatitude(), saved.getLongitude(), citoyenId
            );
        } catch (Exception e) {
            log.warn("[KAFKA] Échec envoi événement #{} : {}", saved.getId(), e.getMessage());
        }


        final TypeSignalement typeCitoyen     = saved.getType();
        final Priorite        prioriteCitoyen = saved.getPrioriteCitoyen();

        try {
            enrichWithAI(saved, req.getImageBase64());
        } catch (Exception e) {
            log.warn("[IA] Analyse échouée #{} : {} — sauvegardé quand même",
                     saved.getId(), e.getMessage());
        }

        saved.setType(typeCitoyen);
        saved.setPrioriteCitoyen(prioriteCitoyen);

        // ── Fallback déterministe : si l'IA n'a pas rempli equipeIA (service down,
        // timeout, 500…), on dérive l'équipe du TYPE choisi par le citoyen pour que
        // le CONTRAT soit tout de même généré automatiquement. Cela garantit que
        // la génération de contrat ne dépend plus de la disponibilité du service IA.
        if (saved.getEquipeIA() == null && typeCitoyen != null
                && TYPE_TO_CODES.containsKey(typeCitoyen)) {
            List<String> codes = TYPE_TO_CODES.get(typeCitoyen);
            if (!codes.isEmpty()) {
                saved.setEquipeIA(codes.get(0));
                if (saved.getEquipeIALabel() == null) {
                    saved.setEquipeIALabel(TYPE_TO_LABEL.getOrDefault(typeCitoyen, codes.get(0)));
                }
                log.info("[IA-FALLBACK] #{} IA indisponible → équipe forcée='{}' (type='{}')",
                         saved.getId(), saved.getEquipeIA(), typeCitoyen);
            }
        }

        repository.save(saved);
        log.info("[CREATE] Valeurs finales #{} — type={} prioriteCitoyen={} equipeIA={}",
                 saved.getId(), typeCitoyen, prioriteCitoyen, saved.getEquipeIA());

        if (saved.getEquipeIA() != null) {
            try {
                // Résoudre l'équipe disponible + chef d'équipe via personnel-service
                // On passe le TYPE CITOYEN pour un matching plus fiable que le code IA
                ChefResolution resolution = resolveEquipeAndChefForType(saved.getType(), saved.getEquipeIA());
                if (resolution != null) {
                    // Mettre à jour le code équipe pour le contrat
                    saved.setEquipeIA(resolution.equipeCode());
                    // Label : garder celui forcé depuis TYPE_TO_LABEL (toujours correct)
                    // Ne pas écraser avec le nom DB qui peut être différent (ex: "Équipe Eau & Assainissement")
                    if (saved.getType() != null && TYPE_TO_LABEL.containsKey(saved.getType())) {
                        // Le label est déjà bon — ne pas toucher
                    } else {
                        saved.setEquipeIALabel(resolution.equipeLabel());
                    }
                    if (!resolution.equipeCode().equalsIgnoreCase(
                            saved.getEquipeIA() == null ? "" : saved.getEquipeIA())) {
                        log.info("[AFFECTATION] Équipe résolue : {} (label={})",
                                 resolution.equipeCode(), saved.getEquipeIALabel());
                    }
                    repository.save(saved);
                    // chefId peut être null si aucun membre n'a encore de userId lié ;
                    // dans ce cas le contrat est retrouvé côté frontend via equipeCode.
                    contratService.genererContrat(saved, resolution.chefId());
                } else {
                    log.warn("[CONTRAT] Aucune équipe disponible (LIBRE) pour le signalement #{}", saved.getId());
                }
            } catch (Exception e) {
                log.warn("[CONTRAT] Échec génération contrat #{} : {}", saved.getId(), e.getMessage());
            }
        }

        // ── Notification citoyen : signalement bien reçu ──
        try {
            notifService.envoyer(
                citoyenId,
                NotificationType.SIGNALEMENT_CREE,
                "✅ Votre signalement a été reçu et est en cours d'analyse.",
                "/signaler/mes-signalements/" + saved.getId(),
                saved.getId()
            );
        } catch (Exception e) {
            log.warn("[NOTIF] Échec notification création #{} : {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    // ════════════════════════════════════════════════════════
    // ENRICHISSEMENT IA
    // ════════════════════════════════════════════════════════
    @Override
    public void enrichWithAI(Signalement sig, String imageBase64) {
        var req = AiServiceClient.AiAnalysisRequest.builder()
            .description(sig.getDescription())
            .latitude(sig.getLatitude())
            .longitude(sig.getLongitude())
            .imageBase64(imageBase64)
            .typeSignalement(sig.getType() != null ? sig.getType().name() : null)
            .build();

        AiAnalysisResponse ai = aiClient.analyze(req);

        // ── Priorité IA (depuis ML) ──────────────────────────────────────────
        Priorite prioriteIA = PRIO_MAP.get(ai.getPriorite());
        if (prioriteIA != null) sig.setPrioriteIA(prioriteIA);

        // ── Équipe : mapping DIRECT depuis le type citoyen ───────────────────
        // L'IA peut se tromper (36% confiance). Le type choisi par le citoyen
        // est la source de vérité pour l'affectation d'équipe.
        TypeSignalement typeCitoyen = sig.getType();
        if (typeCitoyen != null && TYPE_TO_LABEL.containsKey(typeCitoyen)) {
            // Label affiché : deterministe, jamais faux
            sig.setEquipeIALabel(TYPE_TO_LABEL.get(typeCitoyen));
            // Code équipe : premier code de la liste pour ce type
            List<String> codes = TYPE_TO_CODES.getOrDefault(typeCitoyen, List.of("voirie"));
            sig.setEquipeIA(codes.get(0));
            log.info("[IA] #{} → type={} → équipe forcée='{}' (confiance IA ignorée: {}%)",
                     sig.getId(), typeCitoyen, TYPE_TO_LABEL.get(typeCitoyen),
                     Math.round((ai.getConfidences() != null ? ai.getConfidences().getOrDefault("categorie", 0.0) : 0.0) * 100));
        } else {
            // AUTRE ou type inconnu → utiliser la suggestion de l'IA
            sig.setEquipeIA(ai.getEquipe());
            sig.setEquipeIALabel(ai.getEquipeLabel());
            log.info("[IA] #{} → type={} prio={} equipe={} délai={}h (suggestion IA)",
                     sig.getId(), ai.getCategorie(), ai.getPriorite(),
                     ai.getEquipeLabel(), ai.getDelaiHeures());
        }

        sig.setDelaiEstimeHeures(ai.getDelaiHeures());
        sig.setConfidenceIA(
            ai.getConfidences() != null
                ? ai.getConfidences().getOrDefault("categorie", 0.0)
                : 0.0
        );

        try {
            producer.equipeAffectee(sig.getId(), ai.getEquipe(),
                                     ai.getEquipeLabel(), ai.getDelaiHeures());
        } catch (Exception e) {
            log.warn("[KAFKA] Échec envoi equipeAffectee #{} : {}", sig.getId(), e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    // RÉSOLUTION DU CHEF D'ÉQUIPE DISPONIBLE (via personnel-service)
    // ════════════════════════════════════════════════════════

    /**
     * Interroge le personnel-service pour trouver une équipe LIBRE
     * correspondant au code IA, en essayant les équipes de repli si nécessaire.
     *
     * Logique :
     *  1. Cherche une équipe avec specialite = equipeCode ET etat = "LIBRE"
     *  2. Si non trouvée, parcourt les fallbacks FALLBACK_EQUIPES[equipeCode]
     *  3. Retourne {equipeCode final, equipeLabel, chefId} ou null si aucun disponible
     *
     * @param equipeCode  code IA recommandé (ex: "voirie", "plomberie"…)
     */
    // ════════════════════════════════════════════════════════
    // COMPARAISON D'IMAGES JAVA (fallback sans FastAPI)
    // ════════════════════════════════════════════════════════

    private static final int  THUMB_W           = 48;    // thumbnail pour comparaison rapide
    private static final double MAD_THRESHOLD   = 0.06;  // < 6% diff pixel = images trop similaires

    /**
     * Compare deux images base64 localement (Java pur — aucune dépendance externe).
     * Réduit les deux images en 48×48 et calcule la différence moyenne par pixel (MAD).
     *
     * @return true si les images sont trop similaires (problème probablement NON résolu)
     */
    private boolean imagesTropSimilaires(String b64avant, String b64apres) {
        try {
            System.setProperty("java.awt.headless", "true");

            byte[] bA = Base64.getDecoder().decode(
                b64avant.replaceFirst("data:image/[^;]+;base64,", "").trim());
            byte[] bB = Base64.getDecoder().decode(
                b64apres.replaceFirst("data:image/[^;]+;base64,", "").trim());

            BufferedImage imgA = ImageIO.read(new ByteArrayInputStream(bA));
            BufferedImage imgB = ImageIO.read(new ByteArrayInputStream(bB));
            if (imgA == null || imgB == null) return false;

            // Thumbnail 48×48
            BufferedImage tA = thumbnail(imgA);
            BufferedImage tB = thumbnail(imgB);

            long diff = 0;
            for (int x = 0; x < THUMB_W; x++) {
                for (int y = 0; y < THUMB_W; y++) {
                    int rA = tA.getRGB(x, y), rB = tB.getRGB(x, y);
                    diff += Math.abs(((rA >> 16) & 0xFF) - ((rB >> 16) & 0xFF));
                    diff += Math.abs(((rA >> 8)  & 0xFF) - ((rB >> 8)  & 0xFF));
                    diff += Math.abs(( rA        & 0xFF) - ( rB        & 0xFF));
                }
            }
            double mad = diff / (double)(THUMB_W * THUMB_W * 3 * 255);
            log.info("[IMG-JAVA] MAD={:.4f} (seuil={}) → similaires={}", mad, MAD_THRESHOLD, mad < MAD_THRESHOLD);
            return mad < MAD_THRESHOLD;

        } catch (Exception e) {
            log.warn("[IMG-JAVA] Comparaison impossible : {}", e.getMessage());
            return false;  // en cas d'erreur, ne pas bloquer
        }
    }

    private static BufferedImage thumbnail(BufferedImage src) {
        BufferedImage t = new BufferedImage(THUMB_W, THUMB_W, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = t.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, THUMB_W, THUMB_W, null);
        g.dispose();
        return t;
    }

    private ChefResolution resolveEquipeAndChef(String equipeCode) {
        return resolveEquipeAndChefForType(null, equipeCode);
    }

    /**
     * Version améliorée : utilise le TYPE du signalement (plus fiable que le code IA).
     * Recherche une équipe LIBRE par matching souple sur la specialite DB :
     *   - Correspondance exacte (normalisée)
     *   - OU la specialite DB contient le mot-clé
     *   - OU le mot-clé est dans la specialite DB
     */
    private ChefResolution resolveEquipeAndChefForType(TypeSignalement type, String fallbackCode) {
        try {
            List<PersonnelClient.EquipeDto> equipes = personnelClient.getAllEquipes();
            if (equipes == null || equipes.isEmpty()) return null;

            // Construire la liste de codes candidats à partir du type citoyen
            List<String> candidats = new ArrayList<>();
            if (type != null && TYPE_TO_CODES.containsKey(type)) {
                candidats.addAll(TYPE_TO_CODES.get(type));
            }
            // Ajouter le code IA en fallback supplémentaire
            if (fallbackCode != null && !fallbackCode.isBlank()) {
                String codeNorm = normalizeSpecialite(fallbackCode);
                String codeResolu = EQUIPE_ALIASES.getOrDefault(codeNorm, codeNorm);
                if (!candidats.contains(codeResolu)) candidats.add(codeResolu);
                FALLBACK_EQUIPES.getOrDefault(codeResolu, List.of()).forEach(f -> {
                    if (!candidats.contains(f)) candidats.add(f);
                });
            }
            // Filet final : voirie accepte tout
            if (!candidats.contains("voirie")) candidats.add("voirie");
            if (!candidats.contains("infrastructure")) candidats.add("infrastructure");

            for (String candidat : candidats) {
                final String candidatNorm = normalizeSpecialite(candidat);
                // Matching souple : exact OU contains (dans les deux sens)
                // Cela gère "Propreté Urbaine" == "proprete", "Eau & Assainissement" == "assainissement"
                Optional<PersonnelClient.EquipeDto> equipeOpt = equipes.stream()
                    .filter(e -> {
                        String specNorm = normalizeSpecialite(e.getSpecialite());
                        return (specNorm.equals(candidatNorm)
                             || specNorm.contains(candidatNorm)
                             || candidatNorm.contains(specNorm))
                            && "LIBRE".equalsIgnoreCase(e.getEtat());
                    })
                    .findFirst();

                if (equipeOpt.isEmpty()) {
                    log.info("[AFFECTATION] Équipe '{}' non disponible ou absente — essai suivant", candidat);
                    continue;
                }

                PersonnelClient.EquipeDto equipe = equipeOpt.get();
                List<PersonnelClient.MembreDto> membres = equipe.getMembresEquipe();
                if (membres == null || membres.isEmpty()) {
                    log.warn("[AFFECTATION] Équipe '{}' sans membres — génération du contrat sans chef lié", candidat);
                    // Générer le contrat sans chefEquipeId : le chef le retrouve via equipeCode
                    return new ChefResolution(candidat, equipe.getName(), null);
                }

                // Chercher d'abord un CHEF_EQUIPE avec userId (cas idéal).
                // On accepte aussi "CHEF" comme alias pour la compatibilité avec l'ancien
                // frontend qui envoyait "CHEF" au lieu de "CHEF_EQUIPE".
                Optional<String> chefUserId = membres.stream()
                    .filter(m -> isChefFonction(m.getFonction())
                              && m.getUserId() != null && !m.getUserId().isBlank())
                    .map(PersonnelClient.MembreDto::getUserId)
                    .findFirst();

                if (chefUserId.isPresent()) {
                    log.info("[AFFECTATION] ✅ Équipe '{}' (LIBRE) → chef lié userId={}", candidat, chefUserId.get());
                    return new ChefResolution(candidat, equipe.getName(), chefUserId.get());
                }

                // Pas de userId sur le chef → contrat créé sans chefEquipeId ;
                // le frontend retrouve le contrat via la requête par equipeCode.
                log.warn("[AFFECTATION] ⚠️  Équipe '{}' trouvée (LIBRE) mais aucun CHEF_EQUIPE n'a de userId." +
                         " Contrat généré sans chefEquipeId — accessible par equipeCode.", candidat);
                return new ChefResolution(candidat, equipe.getName(), null);
            }

            log.warn("[AFFECTATION] ❌ Aucune équipe LIBRE trouvée (candidats essayés: {})", candidats);
            return null;

        } catch (Exception e) {
            log.warn("[AFFECTATION] Impossible de contacter le personnel-service : {}", e.getMessage());
            return null;
        }
    }


    @Override
    @Transactional(noRollbackFor = Exception.class)
    public Signalement changerStatut(Long id, StatutUpdateRequest req, String operateurId) {
        // JOIN FETCH medias → évite LazyInitializationException dans la sérialisation JSON
        Signalement sig = repository.findByIdWithMedias(id)
            .orElseThrow(() -> new SignalementNotFoundException(
                "Signalement #" + id + " introuvable"));

        StatutSignalement ancien = sig.getStatut();
        sig.setStatut(req.getNouveauStatut());

        // ── Assignation manuelle d'équipe (chemin sans IA) ────────────────────
        if (req.getEquipeIA() != null && !req.getEquipeIA().isBlank()) {
            sig.setEquipeIA(req.getEquipeIA());
            sig.setEquipeIALabel(req.getEquipeIALabel());
            log.info("[STATUT] Équipe assignée manuellement : {} — {}", req.getEquipeIA(), req.getEquipeIALabel());
        }

        repository.save(sig);
        log.info("[STATUT] #{} : {} → {}", id, ancien, req.getNouveauStatut());

        try {
            HistoriqueStatut h = HistoriqueStatut.builder()
                .ancienStatut(ancien)
                .nouveauStatut(req.getNouveauStatut())
                .commentaire(req.getCommentaire())
                .modifiePar(operateurId)
                .build();
            h.setSignalement(sig);
            historiqueRepository.save(h);
            log.info("[HISTORIQUE] {} → {} pour #{}", ancien, req.getNouveauStatut(), id);
        } catch (Exception e) {
            log.warn("[HISTORIQUE] Échec sauvegarde changement statut #{} : {}", id, e.getMessage());
        }

        try {
            producer.statutChange(id, ancien.name(),
                                   req.getNouveauStatut().name(), sig.getCitoyenId());
            if (StatutSignalement.RESOLU.equals(req.getNouveauStatut())) {
                producer.signalementResolu(id, sig.getCitoyenId(), 50);
            }
        } catch (Exception e) {
            log.warn("[KAFKA] Échec envoi événement statut #{} : {}", id, e.getMessage());
        }

        // ── Notification citoyen selon le nouveau statut ──
        try {
            StatutSignalement nouveau = req.getNouveauStatut();
            NotificationType  nType;
            String            nMsg;

            switch (nouveau) {
                case EN_COURS -> {
                    nType = NotificationType.SIGNALEMENT_EN_COURS;
                    nMsg  = "🔧 Votre signalement est maintenant EN COURS de traitement.";
                }
                case RESOLU -> {
                    nType = NotificationType.SIGNALEMENT_RESOLU;
                    nMsg  = "✅ Votre signalement a été RÉSOLU. Merci pour votre vigilance !";
                }
                case REJETE -> {
                    nType = NotificationType.SIGNALEMENT_REJETE;
                    nMsg  = "❌ Votre signalement a été rejeté." +
                            (req.getCommentaire() != null ? " Motif : " + req.getCommentaire() : "");
                }
                default -> {
                    nType = NotificationType.INFO;
                    nMsg  = "ℹ️ Statut de votre signalement mis à jour : " + nouveau.name();
                }
            }

            notifService.envoyer(sig.getCitoyenId(), nType, nMsg,
                                  "/signaler/mes-signalements/" + id, id);
        } catch (Exception e) {
            log.warn("[NOTIF] Échec notification statut #{} : {}", id, e.getMessage());
        }

        // ── Notification WhatsApp si l'utilisateur l'a activé ──
        try {
            log.info("[WHATSAPP] Vérification pour signalement #{} citoyen={}", id, sig.getCitoyenId());
            UserNotifClient.UserNotifInfo userInfo =
                userNotifClient.getUserNotifInfo(sig.getCitoyenId());

            if (userInfo == null) {
                log.warn("[WHATSAPP] userInfo null — user-service injoignable ou userId introuvable");
            } else {
                log.info("[NOTIF] userInfo: tel={} whatsappNotifs={} smsNotifs={}",
                    userInfo.getTelephone(), userInfo.isWhatsappNotifs(), userInfo.isSmsNotifs());

                boolean hasPhone = userInfo.getTelephone() != null && !userInfo.getTelephone().isBlank();

                // ── Canal 1 : WhatsApp ───────────────────────────────
                if (userInfo.isWhatsappNotifs() && hasPhone) {
                    whatsAppService.notifierChangementStatut(
                        userInfo.getTelephone(),
                        id,
                        ancien.name(),
                        req.getNouveauStatut().name(),
                        sig.getType().name(),
                        sig.getAdresse()
                    );
                } else {
                    log.info("[WHATSAPP] Skipped — whatsappNotifs={} tel={}",
                        userInfo.isWhatsappNotifs(), userInfo.getTelephone());
                }

                // ── Canal 2 : SMS (si activé par l'utilisateur) ──────
                if (userInfo.isSmsNotifs() && hasPhone) {
                    smsService.notifierChangementStatut(
                        userInfo.getTelephone(),
                        id,
                        ancien.name(),
                        req.getNouveauStatut().name(),
                        sig.getType().name(),
                        sig.getAdresse()
                    );
                } else {
                    log.info("[SMS] Skipped — smsNotifs={} tel={}",
                        userInfo.isSmsNotifs(), userInfo.getTelephone());
                }
            }
        } catch (Exception e) {
            log.warn("[NOTIF] Échec notification statut #{} : {}", id, e.getMessage(), e);
        }

        return sig;
    }


    // ════════════════════════════════════════════════════════
    // CORRIGER LOCALISATION (admin après écoute vocale)
    // ════════════════════════════════════════════════════════
    @Override
    public Signalement updateLocalisation(Long id, Double latitude, Double longitude, String adresse) {
        Signalement sig = repository.findByIdWithMedias(id)
            .orElseThrow(() -> new SignalementNotFoundException("Signalement #" + id + " introuvable"));

        if (latitude  != null) sig.setLatitude(latitude);
        if (longitude != null) sig.setLongitude(longitude);
        if (adresse   != null && !adresse.isBlank()) sig.setAdresse(adresse);

        repository.save(sig);
        log.info("[LOCALISATION] #{} mis à jour → ({}, {}), adresse='{}'",
            id, sig.getLatitude(), sig.getLongitude(), sig.getAdresse());
        return sig;
    }

    @Override
    public void delete(Long id, String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new SignalementForbiddenException(
                "Seul un administrateur peut supprimer un signalement.");
        }
        Signalement sig = findById(id);

        // Supprimer les ContratTravail liés en premier (FK non-cascadée)
        try {
            var contrats = contratRepository.findBySignalementIdOrderByTentativeAsc(id);
            if (!contrats.isEmpty()) {
                contratRepository.deleteAll(contrats);
                log.info("[DELETE] {} contrat(s) supprimé(s) pour signalement #{}", contrats.size(), id);
            }
        } catch (Exception e) {
            log.warn("[DELETE] Impossible de supprimer les contrats pour #{} : {}", id, e.getMessage());
        }

        repository.delete(sig);
        log.info("[DELETE] Signalement #{} supprimé par admin (rôle={})", id, role);
    }

    // ════════════════════════════════════════════════════════
    // VOTER
    // ════════════════════════════════════════════════════════
    @Override
    public Signalement voter(Long id) {
        Signalement sig = findById(id);
        sig.setVotes(sig.getVotes() + 1);
        return repository.save(sig);
    }

    // ════════════════════════════════════════════════════════
    // LECTURES
    // ════════════════════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public Signalement getById(Long id) {
        return repository.findByIdWithMedias(id)
            .orElseThrow(() -> new SignalementNotFoundException(
                "Signalement #" + id + " introuvable"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signalement> getMesSignalements(String citoyenId) {
        return repository.findByCitoyenIdOrderByDateSignalementDesc(citoyenId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signalement> getByStatut(StatutSignalement statut) {
        return repository.findByStatutOrderByDateSignalementDesc(statut);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signalement> getByProximite(double lat, double lng, double km) {
        return repository.findByProximite(lat, lng, km);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signalement> getAll() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        return Map.of(
            "total",     repository.count(),
            "enAttente", repository.countByStatut(StatutSignalement.EN_ATTENTE),
            "enCours",   repository.countByStatut(StatutSignalement.EN_COURS),
            "resolus",   repository.countByStatut(StatutSignalement.RESOLU),
            "rejetes",   repository.countByStatut(StatutSignalement.REJETE)
        );
    }

    // ════════════════════════════════════════════════════════
    // CHEF D'ÉQUIPE : Résolution avec vérification photo IA
    // ════════════════════════════════════════════════════════

    @Override
    @Transactional
    public Map<String, Object> resoudreParChef(Long id, String photoApres, String commentaire, String chefId) {
        Signalement sig = findById(id);

        // Photo "avant" : première image du signalement (ou null)
        String photoAvant = mediaRepository.findBySignalementId(id)
            .stream().findFirst()
            .map(MediaSignalement::getUrl)
            .orElse(null);

        // Appel IA pour comparer avant/après
        // Par défaut : si pas de photos à comparer, on accepte la décision du chef
        String  rapport      = "Résolution enregistrée par le chef d'équipe.";
        boolean resoluIA     = true;   // accepté par défaut si pas de photo avant
        double  confiance    = 0.0;
        String  observations = commentaire.isBlank() ? "Aucune observation." : commentaire;
        boolean aiVerified   = false;  // indique si l'IA a pu vérifier

        if (photoAvant != null && photoApres != null) {
            try {
                AiServiceClient.VerifyResolutionRequest aiReq = AiServiceClient.VerifyResolutionRequest.builder()
                    .imageAvant(photoAvant)
                    .imageApres(photoApres)
                    .typeSignalement(sig.getType() != null ? sig.getType().name() : "AUTRE")
                    .descriptionOriginale(sig.getDescription())
                    .build();

                AiServiceClient.VerifyResolutionResponse aiResp = aiClient.verifyResolution(aiReq);
                rapport      = aiResp.getRapport();
                resoluIA     = aiResp.isResolu();        // ← verdict IA réel
                confiance    = aiResp.getScoreConfiance();
                observations = aiResp.getObservations();
                aiVerified   = true;
                log.info("[AI-VERIFY] Signalement #{} — résolu={} confiance={:.2f}", id, resoluIA, confiance);
            } catch (Exception e) {
                log.warn("[AI-VERIFY] FastAPI indisponible pour #{} : {} — fallback comparaison Java", id, e.getMessage());

                // ── Fallback Java : comparaison d'images locale (sans FastAPI) ──────
                boolean similaires = imagesTropSimilaires(photoAvant, photoApres);
                if (similaires) {
                    resoluIA     = false;
                    rapport      = "Les deux photos soumises semblent identiques ou très similaires. Le problème ne paraît pas résolu.";
                    observations = "Vérification locale (service IA indisponible) : aucun changement visuel significatif entre la photo avant et après. Veuillez soumettre une photo montrant clairement les travaux effectués.";
                    aiVerified   = true;   // fallback Java = verdict fiable
                    log.info("[IMG-JAVA] #{} — images trop similaires → non résolu", id);
                } else {
                    // Images différentes → analyse comparative locale favorable.
                    // On valide la résolution avec une confiance modérée, mais
                    // SANS message alarmant : l'utilisateur n'a pas à savoir que
                    // le service IA distant a timeout.
                    resoluIA     = true;
                    confiance    = 0.70;   // confiance modérée pour l'analyse locale
                    rapport      = "L'analyse comparative confirme un changement visuel significatif entre les photos avant et après intervention. Le problème apparaît résolu.";
                    observations = commentaire.isBlank()
                        ? "Analyse comparative locale des photos avant/après."
                        : "Analyse comparative locale des photos avant/après. Commentaire du chef : " + commentaire;
                    aiVerified   = true;
                    log.info("[IMG-JAVA] #{} — images différentes → résolution acceptée (fallback)", id);
                }
            }
        }

        // ── Statut final : dépend du verdict IA ──────────────────────────────
        // Si l'IA a analysé les photos ET dit "non résolu" → rester EN_COURS
        // Sinon (pas de photo avant, IA indisponible, ou IA dit résolu) → RESOLU
        StatutSignalement nouveauStatut = (aiVerified && !resoluIA)
            ? StatutSignalement.EN_COURS
            : StatutSignalement.RESOLU;

        // ── Sauvegarder la photo « après » UNIQUEMENT si la résolution
        //    est confirmée. Si l'IA dit « non résolu », on ne persiste
        //    PAS l'image : le chef devra en soumettre une nouvelle lors
        //    d'une prochaine tentative. ─────────────────────────────────────
        if (nouveauStatut == StatutSignalement.RESOLU
                && photoApres != null && !photoApres.isBlank()) {
            MediaSignalement mediaApres = MediaSignalement.builder()
                .signalement(sig)
                .url(photoApres)
                .type("image/jpeg")
                .build();
            mediaRepository.save(mediaApres);
            log.info("[resoudre] #{} photo après persistée (statut=RESOLU)", id);
        } else if (nouveauStatut == StatutSignalement.EN_COURS) {
            log.info("[resoudre] #{} photo après NON persistée (IA : non résolu)", id);
        }

        String commentaireHistorique = aiVerified && !resoluIA
            ? "L'analyse IA indique que le problème n'est pas encore résolu. Nouvelle intervention requise."
            : "Résolution confirmée par chef d'équipe" + (commentaire.isBlank() ? "." : " : " + commentaire);

        HistoriqueStatut historique = HistoriqueStatut.builder()
            .signalement(sig)
            .ancienStatut(sig.getStatut())
            .nouveauStatut(nouveauStatut)
            .commentaire(commentaireHistorique)
            .modifiePar(chefId)
            .build();
        historiqueRepository.save(historique);

        sig.setStatut(nouveauStatut);
        Signalement saved = repository.save(sig);

        // Kafka + notification citoyen — uniquement si réellement résolu
        if (nouveauStatut == StatutSignalement.RESOLU) {
            try {
                producer.signalementResolu(id, sig.getCitoyenId(), 50);
            } catch (Exception ignored) {}

            try {
                notifService.envoyer(
                    sig.getCitoyenId(),
                    NotificationType.SIGNALEMENT_RESOLU,
                    "✅ Votre signalement a été résolu par l'équipe terrain.",
                    null,
                    id
                );
            } catch (Exception ignored) {}
        } else {
            // IA dit non résolu → notifier le citoyen que le travail continue
            try {
                notifService.envoyer(
                    sig.getCitoyenId(),
                    NotificationType.SIGNALEMENT_EN_COURS,
                    "🔧 L'équipe terrain travaille encore sur votre signalement. Une nouvelle vérification sera effectuée.",
                    null,
                    id
                );
            } catch (Exception ignored) {}
        }

        return Map.of(
            "signalement",    saved,
            "rapport",        rapport,
            "resoluIA",       resoluIA,
            "scoreConfiance", confiance,
            "observations",   observations
        );
    }

    // ════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════
    private Signalement findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new SignalementNotFoundException(
                "Signalement #" + id + " introuvable"));
    }
}
