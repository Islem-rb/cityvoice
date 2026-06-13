package tn.cityvoice.evenementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import tn.cityvoice.evenementservice.entity.EvenementSponsor;
import tn.cityvoice.evenementservice.entity.RapportSponsor;
import tn.cityvoice.evenementservice.repository.EvenementSponsorRepository;
import tn.cityvoice.evenementservice.repository.EvenementRepository;
import tn.cityvoice.evenementservice.repository.RapportSponsorRepository;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RapportSponsorService {

    private final RapportSponsorRepository    rapportRepository;
    private final EvenementSponsorRepository  evenementSponsorRepository;
    private final EvenementRepository         evenementRepository;
    private final JavaMailSender              mailSender;

    @Value("${spring.mail.username}")
    private String adminEmail;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:5000")
            .build();

    private static final String FROM_EMAIL = "tasnimabroukii@gmail.com";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    @Transactional
    // ── Générer rapport ───────────────────────────────
    public RapportSponsor genererRapport() {
        log.info("📊 Génération rapport hebdomadaire sponsors...");

        LocalDate today     = LocalDate.now();
        LocalDate weekStart = today.minusDays(7);
        String periode      = weekStart.format(FMT) + " - " + today.format(FMT);

        // Collecter tous les sponsors
        List<EvenementSponsor> sponsors = evenementSponsorRepository.findAll();
        List<Map<String, Object>> sponsorsData    = new ArrayList<>();
        List<Map<String, Object>> evenementsData  = new ArrayList<>();

        Set<Long> evenementIds = new HashSet<>();
        for (EvenementSponsor es : sponsors) {
            Map<String, Object> s = new HashMap<>();
            s.put("nom_entreprise",   es.getSponsor().getNomEntreprise());
            s.put("niveau_sponsorat", es.getNiveauSponsorat());
            s.put("montant_sponsorat",es.getMontantSponsorat());
            s.put("secteur_activite", es.getSponsor().getSecteurActivite());
            s.put("renouvele",        es.getRenouvele());
            s.put("evenement_id",     es.getEvenement().getId());
            sponsorsData.add(s);
            evenementIds.add(es.getEvenement().getId());
        }

        for (Long id : evenementIds) {
            evenementRepository.findById(id).ifPresent(ev -> {
                Map<String, Object> e = new HashMap<>();
                e.put("id",           ev.getId());
                e.put("type",         ev.getType().name());
                e.put("capacite_max", ev.getCapaciteMax());
                e.put("zone",         ev.getZone());
                evenementsData.add(e);
            });
        }

        // Appel Python Flask
        Map<String, Object> payload = new HashMap<>();
        payload.put("periode",    periode);
        payload.put("sponsors",   sponsorsData);
        payload.put("evenements", evenementsData);

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/rapport")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String pdfBase64    = (String) response.get("pdf_base64");
            String analyse      = (String) response.get("analyse");
            Object statsObj     = response.get("stats");
            String statsJson    = statsObj != null ? statsObj.toString() : "{}";

            // Sauvegarder en DB
            RapportSponsor rapport = RapportSponsor.builder()
                    .dateRapport(today)
                    .periode(periode)
                    .pdfBase64(pdfBase64)
                    .analyseOllama(analyse)
                    .statsJson(statsJson)
                    .envoye(false)
                    .build();

            rapport = rapportRepository.save(rapport);

            // Envoyer par email
            envoyerRapportEmail(rapport);

            log.info("✅ Rapport généré et envoyé !");
            return rapport;

        } catch (Exception e) {
            log.error("❌ Erreur génération rapport: {}", e.getMessage());
            throw new RuntimeException("Erreur génération rapport: " + e.getMessage());
        }
    }

    // ── Envoyer email ─────────────────────────────────
    private void envoyerRapportEmail(RapportSponsor rapport) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(FROM_EMAIL, "CityVoice");
            helper.setTo(adminEmail);
            helper.setSubject("📊 Rapport Hebdomadaire Sponsors — " + rapport.getPeriode());
            helper.setText(buildEmailHtml(rapport), true);

            // Attacher PDF
            if (rapport.getPdfBase64() != null) {
                byte[] pdfBytes = Base64.getDecoder().decode(rapport.getPdfBase64());
                helper.addAttachment(
                        "rapport_sponsors_" + rapport.getDateRapport() + ".pdf",
                        new org.springframework.core.io.ByteArrayResource(pdfBytes),
                        "application/pdf"
                );
            }

            mailSender.send(message);
            rapport.setEnvoye(true);
            rapportRepository.save(rapport);
            log.info("📧 Rapport envoyé par email à {}", adminEmail);

        } catch (Exception e) {
            log.error("❌ Erreur envoi email rapport: {}", e.getMessage());
        }
    }

    private String buildEmailHtml(RapportSponsor rapport) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
              <div style="background:#0C1F3F;padding:28px;border-radius:12px 12px 0 0">
                <h1 style="color:#fff;margin:0">📊 Rapport Hebdomadaire</h1>
                <p style="color:rgba(255,255,255,.6);margin:6px 0 0">
                  Sponsors — CityVoice
                </p>
              </div>
              <div style="padding:28px;border:1px solid #e5e7eb;
                          border-top:none;border-radius:0 0 12px 12px">
                <p style="font-size:15px;color:#1a1a2e">
                  Bonjour,
                </p>
                <p style="font-size:14px;color:#4a4a6a">
                  Veuillez trouver ci-joint le rapport hebdomadaire 
                  des sponsors pour la période : 
                  <strong>%s</strong>
                </p>
                <div style="background:#f7f4ef;padding:16px;border-radius:8px;
                            border-left:4px solid #E8532A;margin:20px 0">
                  <p style="margin:0;font-size:13px;color:#0C1F3F">
                    🤖 <strong>Analyse AI :</strong><br/>
                    %s
                  </p>
                </div>
                <p style="font-size:12px;color:#aaa">
                  Email automatique CityVoice — ne pas répondre.
                </p>
              </div>
            </div>
            """.formatted(
                rapport.getPeriode(),
                rapport.getAnalyseOllama() != null
                        ? rapport.getAnalyseOllama().substring(0,
                        Math.min(300, rapport.getAnalyseOllama().length())) + "..."
                        : "Analyse disponible dans le PDF joint."
        );
    }

    // ── Getters ───────────────────────────────────────
    public List<RapportSponsor> getHistorique() {
        return rapportRepository.findAllByOrderByDateRapportDesc();
    }

    public Optional<RapportSponsor> getDernier() {
        return rapportRepository.findTopByOrderByDateRapportDesc();
    }
}