package tn.cityvoice.evenementservice.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.entity.Participant;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

    private final JavaMailSender mailSender;

    private static final String FROM_EMAIL = "tasnimabroukii@gmail.com";
    private static final String FROM_NAME  = "CityVoice";

    public void envoyerInvitation(Evenement ev, Participant p) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(FROM_EMAIL, FROM_NAME);
            helper.setTo(p.getEmailCitoyen());
            helper.setSubject("Invitation : " + ev.getTitre());
            helper.setText(buildHtmlInvitation(ev, p), true);
            mailSender.send(message);
            log.info("📧 Invitation envoyée à {}", p.getEmailCitoyen());
        } catch (Exception e) {
            log.error("Erreur envoi invitation : {}", e.getMessage());
        }
    }

    public void envoyerRappel(Evenement ev, Participant p) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(FROM_EMAIL, FROM_NAME);
            helper.setTo(p.getEmailCitoyen());
            helper.setSubject("⏰ Rappel demain : " + ev.getTitre());
            helper.setText(buildHtmlRappel(ev, p), true);
            mailSender.send(message);
            log.info("⏰ Rappel envoyé à {}", p.getEmailCitoyen());
        } catch (Exception e) {
            log.error("Erreur envoi rappel : {}", e.getMessage());
        }
    }

    private String buildHtmlInvitation(Evenement ev, Participant p) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
              <div style="background:#0C1F3F;padding:28px 32px;border-radius:12px 12px 0 0">
                <h1 style="color:#fff;margin:0;font-size:22px">CityVoice</h1>
                <p style="color:rgba(255,255,255,.5);margin:6px 0 0;font-size:13px">
                  Invitation à un événement
                </p>
              </div>
              <div style="padding:28px 32px;border:1px solid #e5e7eb;
                          border-top:none;border-radius:0 0 12px 12px;background:#fff">
                <p style="font-size:15px;color:#1a1a2e">
                  Bonjour <strong>%s</strong>,
                </p>
                <p style="font-size:14px;color:#4a4a6a;line-height:1.7">
                  Vous êtes invité(e) à participer à :
                </p>
                <div style="background:#f7f4ef;padding:22px;border-radius:10px;
                            margin:20px 0;border-left:4px solid #E8532A">
                  <h2 style="margin:0 0 10px;color:#0C1F3F;font-size:18px">%s</h2>
                  <p style="margin:0 0 12px;color:#4a4a6a;font-size:13px">%s</p>
                  <p style="font-size:13px;color:#1a1a2e">
                    <strong>Date :</strong> %s
                  </p>
                  <p style="font-size:13px;color:#1a1a2e">
                    <strong>Lieu :</strong> %s
                  </p>
                  <p style="font-size:13px;font-weight:700;color:%s">
                    <strong>Prix :</strong> %s
                  </p>
                </div>
                <div style="background:#E6F7F2;padding:14px 18px;border-radius:8px;
                            border-left:3px solid #0D9B76;margin-bottom:20px">
                  <p style="margin:0;font-size:13px;color:#0D9B76;font-weight:600">
                    ⏰ Un rappel vous sera envoyé 24h avant l'événement
                  </p>
                </div>
                <p style="font-size:12px;color:#aaa;margin:0">
                  Email automatique CityVoice — ne pas répondre.
                </p>
              </div>
            </div>
            """.formatted(
                p.getNomCitoyen(),
                ev.getTitre(),
                ev.getDescription() != null ? ev.getDescription() : "",
                ev.getDateDebut() != null ? ev.getDateDebut().toString() : "À définir",
                ev.getLieu(),
                ev.getEstPayant() ? "#E8532A" : "#0D9B76",
                ev.getEstPayant() ? ev.getPrix() + " TND" : "Gratuit"
        );
    }

    private String buildHtmlRappel(Evenement ev, Participant p) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
              <div style="background:#0D9B76;padding:28px 32px;border-radius:12px 12px 0 0">
                <h1 style="color:#fff;margin:0;font-size:22px">⏰ Rappel — Demain !</h1>
                <p style="color:rgba(255,255,255,.6);margin:6px 0 0;font-size:13px">
                  CityVoice — Plateforme citoyenne
                </p>
              </div>
              <div style="padding:28px 32px;border:1px solid #e5e7eb;
                          border-top:none;border-radius:0 0 12px 12px;background:#fff">
                <p style="font-size:15px;color:#1a1a2e">
                  Bonjour <strong>%s</strong>,
                </p>
                <p style="font-size:14px;color:#4a4a6a;line-height:1.7">
                  Rappel : vous êtes inscrit(e) à <strong>%s</strong> demain !
                </p>
                <div style="background:#E6F7F2;padding:22px;border-radius:10px;
                            margin:20px 0;border-left:4px solid #0D9B76">
                  <p style="font-size:14px;color:#1a1a2e;font-weight:700">
                    <strong>Date :</strong> %s
                  </p>
                  <p style="font-size:14px;color:#1a1a2e;font-weight:700">
                    <strong>Lieu :</strong> %s
                  </p>
                </div>
                <p style="font-size:14px;color:#4a4a6a">
                  N'oubliez pas d'être à l'heure ! 🎉
                </p>
                <p style="font-size:12px;color:#aaa;margin:0">
                  Email automatique CityVoice — ne pas répondre.
                </p>
              </div>
            </div>
            """.formatted(
                p.getNomCitoyen(),
                ev.getTitre(),
                ev.getDateDebut() != null ? ev.getDateDebut().toString() : "À définir",
                ev.getLieu()
        );
    }
}