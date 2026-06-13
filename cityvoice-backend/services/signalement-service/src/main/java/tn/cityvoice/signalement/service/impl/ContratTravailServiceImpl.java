package tn.cityvoice.signalement.service.impl;

import tn.cityvoice.signalement.client.PersonnelClient;
import tn.cityvoice.signalement.client.UserNotifClient;
import tn.cityvoice.signalement.dto.ContratReponseRequest;
import tn.cityvoice.signalement.entity.ContratTravail;
import tn.cityvoice.signalement.entity.Signalement;
import tn.cityvoice.signalement.enums.StatutContrat;
import tn.cityvoice.signalement.enums.StatutSignalement;
import tn.cityvoice.signalement.repository.ContratTravailRepository;
import tn.cityvoice.signalement.repository.SignalementRepository;
import tn.cityvoice.signalement.enums.NotificationType;
import tn.cityvoice.signalement.service.IContratTravailService;
import tn.cityvoice.signalement.service.INotificationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContratTravailServiceImpl implements IContratTravailService {

    final ContratTravailRepository contratRepo;
    final SignalementRepository    sigRepo;
    final INotificationService     notifService;
    final PersonnelClient          personnelClient;
    final WhatsAppService          whatsAppService;
    final SmsService               smsService;
    final UserNotifClient          userNotifClient;

    /**
     * Alias IA → codes DB réels (synchronisé avec SignalementServiceImpl).
     * Ex : "assainissement" → "eau_assainissement", "eclairage" → "eclairage_public"
     */
    private static final Map<String, String> EQUIPE_ALIASES = Map.ofEntries(
        Map.entry("assainissement",  "eau_assainissement"),
        Map.entry("eclairage",       "eclairage_public"),
        Map.entry("electricite",     "eclairage_public"),
        Map.entry("eau",             "eau_assainissement"),
        Map.entry("voie",            "voirie"),
        Map.entry("dechets",         "proprete"),
        Map.entry("hygiene",         "proprete"),
        Map.entry("collecte",        "proprete"),
        Map.entry("espace_vert",     "espaces_verts"),
        Map.entry("travaux",         "infrastructure"),
        Map.entry("travaux_publics", "infrastructure")
    );

    /**
     * Fallbacks par spécialité : si l'équipe refuse ou est occupée,
     * chercher parmi les spécialités similaires dans la base.
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
        Map.entry("assainissement",     List.of("eau_assainissement", "plomberie")),
        Map.entry("eclairage",          List.of("eclairage_public", "electricite")),
        Map.entry("dechets",            List.of("proprete", "espaces_verts")),
        Map.entry("hygiene",            List.of("proprete", "espaces_verts"))
    );

    private static final Map<String, String> EQUIPE_LABELS = Map.ofEntries(
        Map.entry("voirie",             "Équipe Voirie"),
        Map.entry("plomberie",          "Équipe Plomberie"),
        Map.entry("electricite",        "Équipe Électricité"),
        Map.entry("espaces_verts",      "Équipe Espaces Verts"),
        Map.entry("proprete",           "Équipe Propreté"),
        Map.entry("infrastructure",     "Équipe Infrastructure"),
        Map.entry("eclairage_public",   "Équipe Éclairage Public"),
        Map.entry("eau_assainissement", "Équipe Eau & Assainissement"),
        Map.entry("assainissement",     "Équipe Eau & Assainissement"),
        Map.entry("eclairage",          "Équipe Éclairage Public"),
        Map.entry("dechets",            "Équipe Propreté"),
        Map.entry("autre",              "Équipe Générale")
    );

    // ══════════════════════════════════════════════════════════════════
    // CRÉATION
    // ══════════════════════════════════════════════════════════════════

    /**
     * Résout le nom complet d'un chef d'équipe depuis son userId.
     * Parcourt toutes les équipes via PersonnelClient pour trouver le membre.
     */
    private String resolveChefNom(String chefUserId) {
        if (chefUserId == null || chefUserId.isBlank()) return null;
        try {
            List<PersonnelClient.EquipeDto> equipes = personnelClient.getAllEquipes();
            for (PersonnelClient.EquipeDto eq : equipes) {
                if (eq.getMembresEquipe() == null) continue;
                for (PersonnelClient.MembreDto m : eq.getMembresEquipe()) {
                    if (chefUserId.equals(m.getUserId()) || chefUserId.equals(m.getId())) {
                        String nom    = m.getNom()    != null ? m.getNom()    : "";
                        String prenom = m.getPrenom() != null ? m.getPrenom() : "";
                        String full   = (prenom + " " + nom).trim();
                        return full.isEmpty() ? null : full;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[CONTRAT] Impossible de résoudre le nom du chef {} : {}", chefUserId, e.getMessage());
        }
        return null;
    }

    @Override
    public ContratTravail genererContrat(Signalement sig, String chefEquipeId) {
        String equipeCode  = sig.getEquipeIA()      != null ? sig.getEquipeIA()      : "autre";
        String equipeLabel = sig.getEquipeIALabel()  != null ? sig.getEquipeIALabel() : "Équipe Générale";

        // Résoudre le nom du chef avant de sauvegarder
        String chefEquipeNom = resolveChefNom(chefEquipeId);

        ContratTravail contrat = ContratTravail.builder()
            .signalement(sig)
            .equipeCode(equipeCode)
            .equipeLabel(equipeLabel)
            .chefEquipeId(chefEquipeId)          // pré-assigné dès la création
            .chefEquipeNom(chefEquipeNom)        // nom résolu depuis le personnel-service
            .tentative(1)
            .delaiEstimeHeures(sig.getDelaiEstimeHeures())
            .numeroContrat(genererNumero(sig.getId(), 1))
            .statut(StatutContrat.EN_ATTENTE_SIGNATURE)
            .build();

        ContratTravail saved = contratRepo.save(contrat);
        log.info("[CONTRAT] Généré #{} → équipe={} chef={} signalement={}",
                 saved.getNumeroContrat(), equipeCode, chefEquipeId, sig.getId());

        // ── Notif citoyen : contrat en cours de traitement ─────────
        try {
            notifService.envoyer(
                sig.getCitoyenId(),
                NotificationType.CONTRAT_GENERE,
                "📋 Un contrat de travail a été généré pour votre signalement — " +
                equipeLabel + " en charge.",
                "/signaler/mes-signalements/" + sig.getId(),
                sig.getId()
            );
        } catch (Exception e) {
            log.warn("[NOTIF] Échec notif contrat généré (citoyen) : {}", e.getMessage());
        }

        // ── Notif chef d'équipe : nouveau contrat à signer ─────────
        if (chefEquipeId != null && !chefEquipeId.isBlank()) {
            try {
                notifService.envoyer(
                    chefEquipeId,
                    NotificationType.CONTRAT_GENERE,
                    "📋 Nouveau contrat à signer — affecté à " + equipeLabel +
                    ". Délai estimé : " +
                    (sig.getDelaiEstimeHeures() != null
                        ? sig.getDelaiEstimeHeures() + "h" : "à définir") + ".",
                    "/chef?tab=contrats",
                    sig.getId()
                );
                log.info("[NOTIF] Chef {} notifié pour contrat #{}", chefEquipeId, saved.getNumeroContrat());
            } catch (Exception e) {
                log.warn("[NOTIF] Échec notif contrat généré (chef) : {}", e.getMessage());
            }
        }

        return saved;
    }

    // ══════════════════════════════════════════════════════════════════
    // ACCEPTATION
    // ══════════════════════════════════════════════════════════════════

    @Override
    public ContratTravail accepterContrat(Long contratId, ContratReponseRequest req,
                                          String chefEquipeId) {
        ContratTravail contrat = findOrThrow(contratId);
        verifierEnAttente(contrat);

        contrat.setStatut(StatutContrat.ACCEPTE);
        contrat.setSignatureBase64(req.getSignatureBase64());
        contrat.setChefEquipeId(chefEquipeId);
        contrat.setDateReponse(LocalDateTime.now());

        // Passer le signalement EN_COURS automatiquement
        Signalement sig = contrat.getSignalement();
        sig.setStatut(tn.cityvoice.signalement.enums.StatutSignalement.EN_COURS);
        sigRepo.save(sig);

        ContratTravail saved = contratRepo.save(contrat);
        log.info("[CONTRAT] Accepté #{} par chef={} → signalement #{} EN_COURS",
                 contrat.getNumeroContrat(), chefEquipeId, sig.getId());

        // Notif citoyen : contrat accepté → signalement EN_COURS
        try {
            notifService.envoyer(
                sig.getCitoyenId(),
                NotificationType.CONTRAT_ACCEPTE,
                "🔧 Votre signalement est maintenant EN COURS — " +
                contrat.getEquipeLabel() + " a accepté d'intervenir.",
                "/signaler/mes-signalements/" + sig.getId(),
                sig.getId()
            );
        } catch (Exception e) {
            log.warn("[NOTIF] Échec notif contrat accepté : {}", e.getMessage());
        }

        // ── Notification WhatsApp + SMS si l'utilisateur l'a activé ──
        notifierCanauxExternes(sig, StatutSignalement.EN_ATTENTE, StatutSignalement.EN_COURS);

        return saved;
    }

    // ══════════════════════════════════════════════════════════════════
    // REFUS + RÉAFFECTATION
    // ══════════════════════════════════════════════════════════════════

    @Override
    public ContratTravail refuserContrat(Long contratId, ContratReponseRequest req,
                                         String chefEquipeId) {
        ContratTravail contrat = findOrThrow(contratId);
        verifierEnAttente(contrat);

        contrat.setStatut(StatutContrat.REFUSE);
        contrat.setMotifRefus(req.getMotifRefus());
        contrat.setChefEquipeId(chefEquipeId);
        contrat.setDateReponse(LocalDateTime.now());
        contratRepo.save(contrat);

        log.info("[CONTRAT] Refusé #{} motif='{}' → réaffectation...",
                 contrat.getNumeroContrat(), req.getMotifRefus());

        ContratTravail nouveau = reassignerVersProchainEquipe(contrat);

        // Notif citoyen : contrat refusé → réaffectation automatique
        Signalement sig = contrat.getSignalement();
        try {
            notifService.envoyer(
                sig.getCitoyenId(),
                NotificationType.CONTRAT_REFUSE,
                "🔄 L'équipe a refusé votre signalement — réaffectation automatique vers " +
                nouveau.getEquipeLabel() + ".",
                "/signaler/mes-signalements/" + sig.getId(),
                sig.getId()
            );
        } catch (Exception e) {
            log.warn("[NOTIF] Échec notif contrat refusé : {}", e.getMessage());
        }

        // ── Notification WhatsApp + SMS — le signalement reste EN_ATTENTE (réaffectation) ──
        notifierCanauxExternes(sig, StatutSignalement.EN_ATTENTE, StatutSignalement.EN_ATTENTE);

        return nouveau;
    }

    /**
     * Envoie les notifications WhatsApp + SMS au citoyen selon ses préférences,
     * en miroir de la logique de SignalementServiceImpl.changerStatut().
     *
     * @param sig      le signalement concerné
     * @param ancien   statut avant changement
     * @param nouveau  statut après changement
     */
    private void notifierCanauxExternes(Signalement sig,
                                        StatutSignalement ancien,
                                        StatutSignalement nouveau) {
        try {
            log.info("[CONTRAT→NOTIF] Canaux externes — signalement #{} citoyen={} ancien={} nouveau={}",
                sig.getId(), sig.getCitoyenId(), ancien, nouveau);

            UserNotifClient.UserNotifInfo userInfo =
                userNotifClient.getUserNotifInfo(sig.getCitoyenId());

            if (userInfo == null) {
                log.warn("[CONTRAT→NOTIF] userInfo null — user-service injoignable ou userId introuvable");
                return;
            }

            log.info("[CONTRAT→NOTIF] userInfo: tel={} whatsappNotifs={} smsNotifs={}",
                userInfo.getTelephone(), userInfo.isWhatsappNotifs(), userInfo.isSmsNotifs());

            boolean hasPhone = userInfo.getTelephone() != null && !userInfo.getTelephone().isBlank();
            String typeName  = sig.getType() != null ? sig.getType().name() : "SIGNALEMENT";
            String adresse   = sig.getAdresse();

            // ── Canal 1 : WhatsApp ───────────────────────────────
            if (userInfo.isWhatsappNotifs() && hasPhone) {
                whatsAppService.notifierChangementStatut(
                    userInfo.getTelephone(),
                    sig.getId(),
                    ancien.name(),
                    nouveau.name(),
                    typeName,
                    adresse
                );
            } else {
                log.info("[CONTRAT→WHATSAPP] Skipped — whatsappNotifs={} tel={}",
                    userInfo.isWhatsappNotifs(), userInfo.getTelephone());
            }

            // ── Canal 2 : SMS (si activé par l'utilisateur) ──────
            if (userInfo.isSmsNotifs() && hasPhone) {
                smsService.notifierChangementStatut(
                    userInfo.getTelephone(),
                    sig.getId(),
                    ancien.name(),
                    nouveau.name(),
                    typeName,
                    adresse
                );
            } else {
                log.info("[CONTRAT→SMS] Skipped — smsNotifs={} tel={}",
                    userInfo.isSmsNotifs(), userInfo.getTelephone());
            }
        } catch (Exception e) {
            log.warn("[CONTRAT→NOTIF] Échec canaux externes : {}", e.getMessage(), e);
        }
    }

    /**
     * Réaffecte le signalement vers une autre équipe après refus du chef.
     *
     * Logique :
     *  1. Résoudre l'alias IA de l'équipe refusante (ex: "assainissement" → "eau_assainissement")
     *  2. Chercher dans la liste des fallbacks une équipe LIBRE en base (personnel-service)
     *  3. Si personnel-service indisponible : utiliser la liste statique FALLBACK_EQUIPES
     *  4. Générer le nouveau contrat avec le chef lié à l'équipe de remplacement
     */
    private ContratTravail reassignerVersProchainEquipe(ContratTravail contratRefuse) {
        String equipeActuelle = normalizeCode(contratRefuse.getEquipeCode());
        int    tentative      = contratRefuse.getTentative();
        Signalement sig       = contratRefuse.getSignalement();

        // Équipes déjà essayées pour ce signalement
        List<ContratTravail> historique = contratRepo
            .findBySignalementIdOrderByTentativeAsc(sig.getId());
        Set<String> dejaTentees = new HashSet<>();
        historique.forEach(c -> dejaTentees.add(normalizeCode(c.getEquipeCode())));

        // Construire la liste des candidats à essayer (alias + fallbacks)
        String equipeAliasee = EQUIPE_ALIASES.getOrDefault(equipeActuelle, equipeActuelle);
        List<String> candidats = new ArrayList<>();
        FALLBACK_EQUIPES.getOrDefault(equipeAliasee,  List.of()).forEach(f -> {
            if (!dejaTentees.contains(f) && !candidats.contains(f)) candidats.add(f);
        });
        FALLBACK_EQUIPES.getOrDefault(equipeActuelle, List.of()).forEach(f -> {
            if (!dejaTentees.contains(f) && !candidats.contains(f)) candidats.add(f);
        });
        if (candidats.isEmpty()) candidats.add("autre");

        // ── Tenter via personnel-service (équipes LIBRES en base) ─────────────
        String prochaineCode  = null;
        String prochaineLabel = null;
        String prochainChefId = null;

        try {
            List<PersonnelClient.EquipeDto> equipes = personnelClient.getAllEquipes();
            if (equipes != null) {
                for (String candidat : candidats) {
                    final String candNorm = normalizeCode(candidat);
                    Optional<PersonnelClient.EquipeDto> eq = equipes.stream()
                        .filter(e -> candNorm.equalsIgnoreCase(normalizeCode(e.getSpecialite()))
                                  && "LIBRE".equalsIgnoreCase(e.getEtat()))
                        .findFirst();
                    if (eq.isPresent()) {
                        PersonnelClient.EquipeDto found = eq.get();
                        prochaineCode  = candNorm;
                        prochaineLabel = found.getName() != null ? found.getName()
                            : EQUIPE_LABELS.getOrDefault(candNorm, "Équipe Générale");
                        // Chercher le chef lié
                        if (found.getMembresEquipe() != null) {
                            prochainChefId = found.getMembresEquipe().stream()
                                .filter(m -> isChefFonction(m.getFonction())
                                          && m.getUserId() != null && !m.getUserId().isBlank())
                                .map(PersonnelClient.MembreDto::getUserId)
                                .findFirst()
                                .orElse(null);
                        }
                        log.info("[REASSIGN] Équipe libre trouvée en base : '{}' (chef={})",
                            prochaineCode, prochainChefId);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[REASSIGN] Personnel-service indisponible — fallback statique : {}", ex.getMessage());
        }

        // Fallback statique si personnel-service indisponible ou aucune équipe libre
        if (prochaineCode == null) {
            prochaineCode = candidats.get(0);
            prochaineLabel = EQUIPE_LABELS.getOrDefault(prochaineCode, "Équipe Générale");
            log.info("[REASSIGN] Fallback statique utilisé → '{}'", prochaineCode);
        }

        ContratTravail nouveau = ContratTravail.builder()
            .signalement(sig)
            .equipeCode(prochaineCode)
            .equipeLabel(prochaineLabel)
            .chefEquipeId(prochainChefId)                       // chef de la nouvelle équipe
            .tentative(tentative + 1)
            .delaiEstimeHeures(sig.getDelaiEstimeHeures())
            .numeroContrat(genererNumero(sig.getId(), tentative + 1))
            .statut(StatutContrat.EN_ATTENTE_SIGNATURE)
            .contratParentId(contratRefuse.getId())
            .build();

        sig.setEquipeIA(prochaineCode);
        sig.setEquipeIALabel(prochaineLabel);
        sigRepo.save(sig);

        ContratTravail saved = contratRepo.save(nouveau);
        log.info("[CONTRAT] Réaffecté #{} → équipe='{}' chef={} (tentative {})",
                 saved.getNumeroContrat(), prochaineCode, prochainChefId, tentative + 1);
        return saved;
    }

    /** Normalise un code d'équipe (accents, casse, espaces). */
    private static String normalizeCode(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[\\s_-]+", "_")
            .replaceAll("[^a-z0-9_]", "");
    }

    /** Accepte "CHEF_EQUIPE" et "CHEF" comme fonctions chef valides. */
    private static boolean isChefFonction(String f) {
        return f != null && ("CHEF_EQUIPE".equalsIgnoreCase(f) || "CHEF".equalsIgnoreCase(f));
    }

    // ══════════════════════════════════════════════════════════════════
    // LECTURE
    // ══════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public ContratTravail getById(Long id) {
        return findOrThrow(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ContratTravail getByNumero(String numero) {
        return contratRepo.findByNumeroContrat(numero)
            .orElseThrow(() -> new EntityNotFoundException("Contrat introuvable : " + numero));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContratTravail> getContratActifParSignalement(Long sigId) {
        return contratRepo.findTopBySignalementIdOrderByTentativeDesc(sigId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratTravail> getHistoriqueParSignalement(Long sigId) {
        return contratRepo.findBySignalementIdOrderByTentativeAsc(sigId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratTravail> getContratsEnAttente() {
        return contratRepo.findByStatutOrderByDateCreationDesc(StatutContrat.EN_ATTENTE_SIGNATURE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratTravail> getTousLesContrats() {
        return contratRepo.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratTravail> getContratsParChef(String chefEquipeId) {
        // Retourne les contrats explicitement assignés à ce chef
        // + les contrats EN_ATTENTE de son équipe (si chefId est codé comme equipeCode)
        return contratRepo.findByChefEquipeIdOrderByDateCreationDesc(chefEquipeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratTravail> getContratsEnAttenteParEquipe(String equipeCode) {
        return contratRepo.findByEquipeCodeAndStatutOrderByDateCreationDesc(
            equipeCode, StatutContrat.EN_ATTENTE_SIGNATURE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratTravail> getContratsParEquipe(String equipeCode) {
        return contratRepo.findByEquipeCodeOrderByDateCreationDesc(equipeCode);
    }

    // ══════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════

    private ContratTravail findOrThrow(Long id) {
        return contratRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Contrat introuvable : " + id));
    }

    private void verifierEnAttente(ContratTravail c) {
        if (c.getStatut() != StatutContrat.EN_ATTENTE_SIGNATURE) {
            throw new IllegalStateException(
                "Ce contrat n'est plus en attente de signature (statut=" + c.getStatut() + ")");
        }
    }

    private String genererNumero(Long sigId, int tentative) {
        String annee   = String.valueOf(LocalDateTime.now().getYear());
        String mois    = String.format("%02d", LocalDateTime.now().getMonthValue());
        String seq     = String.format("%05d", sigId);
        String attempt = tentative > 1 ? "-T" + tentative : "";
        return "MDN-" + annee + mois + "-" + seq + attempt;
    }
}
