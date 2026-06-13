package tn.cityvoice.evenementservice.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.evenementservice.dto.request.CreateEvenementRequest;
import tn.cityvoice.evenementservice.dto.request.InscriptionRequest;
import tn.cityvoice.evenementservice.dto.response.EvenementResponse;
import tn.cityvoice.evenementservice.dto.response.StatsResponse;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.entity.EvenementNotification;
import tn.cityvoice.evenementservice.entity.Participant;
import tn.cityvoice.evenementservice.enums.StatutEvenement;
import tn.cityvoice.evenementservice.exception.EvenementCompletException;
import tn.cityvoice.evenementservice.exception.EvenementNotFoundException;
import tn.cityvoice.evenementservice.repository.EvenementRepository;
import tn.cityvoice.evenementservice.repository.ParticipantRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class EvenementService {

    private final EvenementRepository evenementRepository;
    private final ParticipantRepository participantRepository;
    private final InvitationService invitationService;
    private final RappelService rappelService;
    private final EvenementNotificationService notificationService;

    // Créer un événement
    public EvenementResponse creerEvenement(CreateEvenementRequest req) {
        Evenement ev = Evenement.builder()
                .titre(req.getTitre())
                .description(req.getDescription())
                .type(req.getType())
                .dateDebut(req.getDateDebut())
                .dateFin(req.getDateFin())
                .lieu(req.getLieu())
                .capaciteMax(req.getCapaciteMax())
                .estPayant(req.getEstPayant())
                .prix(req.getPrix())
                .organisateurId(req.getOrganisateurId())
                .imageUrl(req.getImageUrl())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .typeLieu(req.getTypeLieu())
                .zone(req.getZone())
                .mediaPrevu(req.getMediaPrevu() != null ? req.getMediaPrevu() : false)
                .streamingPrevu(req.getStreamingPrevu() != null ? req.getStreamingPrevu() : false)
                .budgetEvenement(req.getBudgetEvenement())
                .budgetReel(req.getBudgetReel())
                .build();

        Evenement saved = evenementRepository.save(ev);
        log.info("Événement créé : {}", saved.getId());
        return toResponse(saved);
    }

    // Lister tous les événements publiés
    @Transactional(readOnly = true)
    public List<EvenementResponse> listerEvenementsPublies() {
        return evenementRepository
                .findByStatutOrderByDateDebutAsc(StatutEvenement.PUBLIE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Lister tous les événements (admin)
    @Transactional(readOnly = true)
    public List<EvenementResponse> listerTous() {
        return evenementRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Trouver par ID
    @Transactional(readOnly = true)
    public EvenementResponse getById(Long id) {
        return toResponse(findById(id));
    }

    // Publier un événement
    public EvenementResponse publierEvenement(Long id) {
        Evenement ev = findById(id);
        ev.setStatut(StatutEvenement.PUBLIE);
        log.info("Événement publié : {}", id);
        return toResponse(evenementRepository.save(ev));
    }

    // Annuler un événement
    public EvenementResponse annulerEvenement(Long id) {
        Evenement ev = findById(id);
        ev.setStatut(StatutEvenement.ANNULE);
        // ← Notifier tous les inscrits
        List<Participant> inscrits = participantRepository.findByEvenement_Id(id);
        inscrits.forEach(p -> notificationService.creer(
                p.getCitoyenId(),
                "Événement annulé ❌",
                "L'événement \"" + ev.getTitre() + "\" a été annulé.",
                EvenementNotification.TypeNotification.EVENEMENT_ANNULE,
                ev.getId(),
                ev.getTitre()
        ));
        log.info("Événement annulé : {}", id);
        return toResponse(evenementRepository.save(ev));
    }

    // Inscrire un participant
    public Participant inscrireParticipant(Long evenementId, InscriptionRequest req) {
        Evenement ev = findById(evenementId);

        // Vérifier si l'événement est complet
        if (ev.estComplet())
            throw new EvenementCompletException("Cet événement est complet");

        // Vérifier si déjà inscrit
        boolean dejaInscrit = participantRepository
                .existsByEvenement_IdAndCitoyenId(evenementId, req.getCitoyenId());
        if (dejaInscrit)
            throw new RuntimeException("Vous êtes déjà inscrit à cet événement");

        // Créer le participant
        Participant participant = Participant.builder()
                .evenement(ev)
                .citoyenId(req.getCitoyenId())
                .emailCitoyen(req.getEmail())
                .nomCitoyen(req.getNom())
                .telCitoyen(req.getTelCitoyen())
                .qrToken(UUID.randomUUID().toString())
                .statutPresence(Participant.StatutPresence.EN_ATTENTE)
                .confirme(false)
                .rappelEnvoye(false)
                .build();

        Participant p =participantRepository.save(participant);

        // Envoyer invitation email
        invitationService.envoyerInvitation(ev, p);

        // Programmer rappel 24h avant
        rappelService.programmerRappel(ev, p);
        // ← Notifier le citoyen
        notificationService.creer(
                p.getCitoyenId(),
                "Inscription confirmée ✅",
                "Vous êtes inscrit à : \"" + ev.getTitre() + "\"",
                EvenementNotification.TypeNotification.INSCRIPTION,
                ev.getId(),
                ev.getTitre()
        );
        log.info("Participant {} inscrit à l'événement {}", req.getCitoyenId(), evenementId);
        return p;
    }

    // Supprimer un événement
    public void supprimerEvenement(Long id) {
        Evenement ev = findById(id);
        evenementRepository.delete(ev);
        log.info("Événement supprimé : {}", id);
    }

    // Méthode utilitaire interne
    public Evenement findById(Long id) {
        return evenementRepository.findById(id)
                .orElseThrow(() -> new EvenementNotFoundException(
                        "Événement introuvable avec l'id : " + id));
    }

    // Mapper Entité → Response
    public EvenementResponse toResponse(Evenement ev) {
        return EvenementResponse.builder()
                .id(ev.getId())
                .titre(ev.getTitre())
                .description(ev.getDescription())
                .type(ev.getType())
                .statut(ev.getStatut())
                .dateDebut(ev.getDateDebut())
                .dateFin(ev.getDateFin())
                .lieu(ev.getLieu())
                .capaciteMax(ev.getCapaciteMax())
                .nbInscrits(ev.getParticipants().size())
                .estPayant(ev.getEstPayant())
                .prix(ev.getPrix())
                .organisateurId(ev.getOrganisateurId())
                .imageUrl(ev.getImageUrl())
                .latitude(ev.getLatitude())
                .longitude(ev.getLongitude())
                .createdAt(ev.getCreatedAt())
                .typeLieu(ev.getTypeLieu())
                .zone(ev.getZone())
                .mediaPrevu(ev.getMediaPrevu())
                .streamingPrevu(ev.getStreamingPrevu())
                .budgetEvenement(ev.getBudgetEvenement())
                .budgetReel(ev.getBudgetReel())
                .build();
    }

    public EvenementResponse modifierEvenement(Long id, CreateEvenementRequest req) {
        Evenement ev = findById(id);
        ev.setTitre(req.getTitre());
        ev.setDescription(req.getDescription());
        ev.setType(req.getType());
        ev.setDateDebut(req.getDateDebut());
        ev.setDateFin(req.getDateFin());
        ev.setLieu(req.getLieu());
        ev.setCapaciteMax(req.getCapaciteMax());
        ev.setEstPayant(req.getEstPayant());
        ev.setPrix(req.getPrix());
        ev.setImageUrl(req.getImageUrl());
        ev.setLatitude(req.getLatitude());
        ev.setLongitude(req.getLongitude());
        ev.setTypeLieu(req.getTypeLieu());
        ev.setZone(req.getZone());
        ev.setMediaPrevu(req.getMediaPrevu() != null ? req.getMediaPrevu() : false);
        ev.setStreamingPrevu(req.getStreamingPrevu() != null ? req.getStreamingPrevu() : false);
        ev.setBudgetEvenement(req.getBudgetEvenement());
        ev.setBudgetReel(req.getBudgetReel());
        Evenement saved = evenementRepository.save(ev);
        // ← Notifier tous les participants
        List<Participant> participants = participantRepository
                .findByEvenement_Id(id);
        log.info("Participants trouvés pour événement {} : {}", id, participants.size()); // debug
        participants.forEach(p -> {
            notificationService.creer(
                    p.getCitoyenId(),
                    "📝 Événement modifié",
                    "L'événement \"" + saved.getTitre() + "\" a été mis à jour. Vérifiez les nouvelles informations.",
                    EvenementNotification.TypeNotification.MODIFICATION,
                    saved.getId(),
                    saved.getTitre()
            );
        });

        log.info("Événement modifié : {} — {} participants notifiés", id, participants.size());
        return toResponse(saved);
    }
    // Lister participants d'un événement
    @Transactional(readOnly = true)
    public List<Participant> getParticipants(Long evenementId) {
        findById(evenementId); // vérifier que l'événement existe
        return participantRepository.findByEvenement_Id(evenementId);
    }

    // Supprimer un participant
    public void supprimerParticipant(Long participantId) {
        Participant p = participantRepository.findById(participantId)
                .orElseThrow(() -> new RuntimeException("Participant introuvable"));
        participantRepository.delete(p);
        log.info("Participant {} supprimé", participantId);
    }

    // Confirmer présence manuellement
    public Participant confirmerPresence(Long participantId) {
        Participant p = participantRepository.findById(participantId)
                .orElseThrow(() -> new RuntimeException("Participant introuvable"));
        p.setStatutPresence(Participant.StatutPresence.CONFIRME);
        p.setDatePresenceConfirmee(java.time.LocalDateTime.now());
        return participantRepository.save(p);
    }
    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        List<Evenement> tous = evenementRepository.findAll();

        // ── KPIs ─────────────────────────────────────────
        long totalEvenements    = tous.size();
        long evenementsPublies  = tous.stream()
                .filter(e -> e.getStatut() == StatutEvenement.PUBLIE).count();
        long evenementsBrouillon = tous.stream()
                .filter(e -> e.getStatut() == StatutEvenement.BROUILLON).count();
        long evenementsAnnules  = tous.stream()
                .filter(e -> e.getStatut() == StatutEvenement.ANNULE).count();
        long totalInscrits      = tous.stream()
                .mapToLong(e -> e.getParticipants().size()).sum();
        double totalRevenus     = tous.stream()
                .filter(e -> e.getEstPayant() && e.getPrix() != null)
                .mapToDouble(e -> e.getPrix().doubleValue() * e.getParticipants().size())
                .sum();

        // Taux remplissage moyen
        double tauxMoyen = tous.stream()
                .filter(e -> e.getCapaciteMax() != null && e.getCapaciteMax() > 0)
                .mapToDouble(e -> (double) e.getParticipants().size() / e.getCapaciteMax() * 100)
                .average().orElse(0.0);

        // ── Répartitions ─────────────────────────────────
        Map<String, Long> parType = tous.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getType().name(),
                        java.util.stream.Collectors.counting()
                ));

        Map<String, Long> parStatut = tous.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getStatut().name(),
                        java.util.stream.Collectors.counting()
                ));

        Map<String, Long> parZone = tous.stream()
                .filter(e -> e.getZone() != null && !e.getZone().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(
                        Evenement::getZone,
                        java.util.stream.Collectors.counting()
                ));

        Map<String, Long> parTypeLieu = tous.stream()
                .filter(e -> e.getTypeLieu() != null && !e.getTypeLieu().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(
                        Evenement::getTypeLieu,
                        java.util.stream.Collectors.counting()
                ));

        // ── Tendances par mois ────────────────────────────
        String[] mois = {"Jan","Fev","Mar","Avr","Mai","Jun",
                "Jul","Aou","Sep","Oct","Nov","Dec"};

        Map<String, Long> inscriptionsParMois = new java.util.LinkedHashMap<>();
        Map<String, Long> evenementsParMois   = new java.util.LinkedHashMap<>();

        for (String m : mois) {
            inscriptionsParMois.put(m, 0L);
            evenementsParMois.put(m, 0L);
        }

        tous.forEach(e -> {
            if (e.getDateDebut() != null) {
                String m = mois[e.getDateDebut().getMonthValue() - 1];
                evenementsParMois.merge(m, 1L, Long::sum);
                inscriptionsParMois.merge(m,
                        (long) e.getParticipants().size(), Long::sum);
            }
        });

        // ── Top 5 inscrits ────────────────────────────────
        List<EvenementResponse> top5 = tous.stream()
                .sorted((a, b) -> b.getParticipants().size() - a.getParticipants().size())
                .limit(5)
                .map(this::toResponse)
                .toList();

        // ── Événements cette semaine ──────────────────────
        java.time.LocalDateTime maintenant  = java.time.LocalDateTime.now();
        java.time.LocalDateTime finSemaine  = maintenant.plusDays(7);

        List<EvenementResponse> cetteSemaine = tous.stream()
                .filter(e -> e.getDateDebut() != null
                        && e.getDateDebut().isAfter(maintenant)
                        && e.getDateDebut().isBefore(finSemaine))
                .map(this::toResponse)
                .toList();

        return StatsResponse.builder()
                .totalEvenements(totalEvenements)
                .evenementsPublies(evenementsPublies)
                .evenementsBrouillon(evenementsBrouillon)
                .evenementsAnnules(evenementsAnnules)
                .totalInscrits(totalInscrits)
                .totalRevenus(totalRevenus)
                .tauxRemplissageMoyen(Math.round(tauxMoyen * 10.0) / 10.0)
                .parType(parType)
                .parStatut(parStatut)
                .parZone(parZone)
                .parTypeLieu(parTypeLieu)
                .inscriptionsParMois(inscriptionsParMois)
                .evenementsParMois(evenementsParMois)
                .top5Inscrits(top5)
                .evenementsCetteSemaine(cetteSemaine)
                .build();
    }
}