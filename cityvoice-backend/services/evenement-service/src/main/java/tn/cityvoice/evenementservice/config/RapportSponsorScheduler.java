package tn.cityvoice.evenementservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.cityvoice.evenementservice.service.RapportSponsorService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RapportSponsorScheduler {

    private final RapportSponsorService rapportService;

    // Chaque lundi à 8h00
    @Scheduled(cron = "0 0 8 * * MON")
    public void genererRapportHebdomadaire() {
        log.info("⏰ Déclenchement automatique rapport sponsors...");
        try {
            rapportService.genererRapport();
        } catch (Exception e) {
            log.error("❌ Erreur scheduler rapport: {}", e.getMessage());
        }
    }
}