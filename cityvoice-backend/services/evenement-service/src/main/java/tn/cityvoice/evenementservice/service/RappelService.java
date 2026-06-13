package tn.cityvoice.evenementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.entity.Participant;
import tn.cityvoice.evenementservice.repository.ParticipantRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Qualifier;
@Service
@Slf4j

public class RappelService {

    private final TaskScheduler        taskScheduler;
    private final InvitationService    invitationService;   // email
    private final WhatsAppService      whatsAppService;     // ➕ WhatsApp
    private final ParticipantRepository participantRepository;
    public RappelService(
            @Qualifier("rappelTaskScheduler") TaskScheduler taskScheduler,
            InvitationService invitationService,
            WhatsAppService whatsAppService,
            ParticipantRepository participantRepository) {
        this.taskScheduler = taskScheduler;
        this.invitationService = invitationService;
        this.whatsAppService = whatsAppService;
        this.participantRepository = participantRepository;
    }
    public void programmerRappel(Evenement ev, Participant p) {
        log.info("🔔 programmerRappel appelé pour {} - dateDebut: {}",
                p.getEmailCitoyen(), ev.getDateDebut());
        if (ev.getDateDebut() == null) return;

        //LocalDateTime heureRappel = ev.getDateDebut().minusHours(24);
        LocalDateTime heureRappel = LocalDateTime.now().plusMinutes(3);
        log.info("⏰ heureRappel calculée: {} | maintenant: {}",
                heureRappel, LocalDateTime.now());

        if (heureRappel.isBefore(LocalDateTime.now())) {
            log.warn("Rappel non programmé - date passée pour événement {}", ev.getId());
            return;
        }

        taskScheduler.schedule(() -> {
            try {
                log.info("⏰ Envoi rappel pour {} - événement {}",
                        p.getEmailCitoyen(), ev.getId());

                // Recharger le participant depuis la DB
                Participant pFresh = participantRepository.findById(p.getId())
                        .orElse(p);

                invitationService.envoyerRappel(ev, pFresh);
                whatsAppService.envoyerRappelWhatsApp(ev, pFresh);

                pFresh.setRappelEnvoye(true);
                participantRepository.save(pFresh);

            } catch (Exception e) {
                log.error("❌ Erreur rappel pour {} : {}",
                        p.getEmailCitoyen(), e.getMessage());
            }
        }, heureRappel.atZone(java.time.ZoneId.systemDefault()).toInstant());

        log.info("📅 Rappel programmé pour {} à {}",
                p.getEmailCitoyen(), heureRappel);
    }
}