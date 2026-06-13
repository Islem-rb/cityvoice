package tn.cityvoice.projetservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;
import tn.cityvoice.projetservice.service.OllamaService;

import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailReceiptService {

    private final JavaMailSender mailSender;
    private final OllamaService  ollamaService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendReceipt(

            String toEmail,
            String nom,
            String projetTitre,
            float  montant,
            int    points,
            String reference,
            String methode) {
        log.info("=== SENDING RECEIPT ===");
        log.info("To: {} | Name: {} | Projet: {} | Montant: {}", toEmail, nom, projetTitre, montant);
        log.info("fromEmail configured as: {}", fromEmail);
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("No email — skipping receipt");
            return;
        }

        try {
            // 1 — AI generates personal message
            String aiMessage = generatePersonalMessage(
                    nom, projetTitre, montant, points
            );
            log.info("AI message generated for {}", nom);

            // 2 — Build XHTML (must be valid XML for PDF)
            String xhtml = buildXhtml(
                    nom, projetTitre, montant, points,
                    reference, methode, aiMessage
            );

            // 3 — Convert XHTML → PDF bytes
            byte[] pdfBytes = convertToPdf(xhtml);
            log.info("PDF generated: {} bytes", pdfBytes.length);

            // 4 — Send email with PDF attachment
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    msg, true, "UTF-8"
            );
            helper.setFrom(fromEmail, "Madina · Projets Urbains");
            helper.setTo(toEmail);
            helper.setSubject("✅ Reçu de don — " + projetTitre);

            // Email body (simple)
            helper.setText(buildSimpleEmailBody(nom, projetTitre, montant), true);

            // PDF attachment
            helper.addAttachment(
                    "Recu-Madina-" + projetTitre.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf",
                    new org.springframework.core.io.ByteArrayResource(pdfBytes),
                    "application/pdf"
            );

            mailSender.send(msg);
            log.info("Receipt PDF sent to {}", toEmail);

        } catch (Exception e) {
            log.error("Receipt failed for {}: {}", toEmail, e.getMessage(), e);
        }
    }

    // ── AI: generates unique personal message ─────────────
    private String generatePersonalMessage(
            String nom,
            String projetTitre,
            float  montant,
            int    points) {

        String firstName = nom.split(" ")[0];
        String prompt = String.format("""
            Write a warm sincere thank-you message in French for a civic donation receipt.
            Donor first name: %s
            Project supported: %s
            Amount donated: %.0f DT
            Points earned: %d

            Rules:
            - Exactly 2-3 sentences
            - Warm and personal, use the first name
            - Mention positive city impact
            - Never use "Cher(e)" or "utilisateur"
            - Output ONLY the message, no quotes, no extra text
            /no_think
            """, firstName, projetTitre, montant, points);

        try {
            String raw = ollamaService.generateContent(prompt);
            String clean = raw
                    .replaceAll("(?s)<think>.*?</think>", "")
                    .replaceAll("[\"«»]", "")
                    .trim();
            if (clean.isBlank() || clean.length() < 20) {
                return fallbackMessage(firstName, projetTitre, montant);
            }
            return clean;
        } catch (Exception e) {
            log.warn("AI message failed: {}", e.getMessage());
            return fallbackMessage(firstName, projetTitre, montant);
        }
    }

    private String fallbackMessage(String firstName, String projet, float montant) {
        return firstName + ", merci pour votre don de " + (int) montant
                + " DT en faveur du projet « " + projet + " ». "
                + "Votre engagement citoyen contribue à bâtir une ville meilleure pour tous.";
    }

    // ── Convert XHTML string → PDF bytes ──────────────────
    private byte[] convertToPdf(String xhtml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(xhtml);
        renderer.layout();
        renderer.createPDF(out);
        return out.toByteArray();
    }

    // ── Simple HTML email body (the PDF is the real receipt) ─
    private String buildSimpleEmailBody(
            String nom, String projetTitre, float montant) {

        return """
        <!DOCTYPE html>
        <html><body style="font-family:Arial,sans-serif;background:#F7F4EF;
                           padding:40px 20px;margin:0;">
          <table width="100%%" cellpadding="0" cellspacing="0">
          <tr><td align="center">
            <table width="540" style="background:#fff;border-radius:16px;
                                       padding:40px;max-width:540px;">
              <tr><td style="text-align:center;padding-bottom:24px;">
                <span style="font-size:24px;font-weight:900;color:#0C1F3F;">
                  Madina
                </span>
              </td></tr>
              <tr><td style="text-align:center;padding-bottom:20px;">
                <span style="font-size:40px;">✅</span>
                <p style="font-size:18px;font-weight:700;color:#0C1F3F;margin:8px 0 0;">
                  Don confirmé
                </p>
              </td></tr>
              <tr><td style="font-size:14px;color:#666;line-height:1.7;
                              padding-bottom:24px;">
                Bonjour %s,<br><br>
                Votre don de <strong style="color:#0C1F3F;">%.2f DT</strong>
                pour le projet <strong style="color:#0C1F3F;">%s</strong>
                a bien été enregistré.<br><br>
                Votre reçu complet est joint en PDF à cet email.
              </td></tr>
              <tr><td style="text-align:center;padding-bottom:24px;">
                <a href="http://localhost:4200/projets"
                   style="display:inline-block;padding:12px 32px;
                          background:#E8532A;color:#fff;border-radius:100px;
                          text-decoration:none;font-weight:700;font-size:14px;">
                  Voir les projets →
                </a>
              </td></tr>
              <tr><td style="text-align:center;font-size:11px;color:#bbb;
                              border-top:1px solid #eee;padding-top:20px;">
                Madina · Plateforme de participation citoyenne · Tunis
              </td></tr>
            </table>
          </td></tr>
          </table>
        </body></html>
        """.formatted(nom, montant, projetTitre);
    }

    // ── XHTML for PDF (must be valid XML) ──────────────────
    private String buildXhtml(
            String nom,
            String projetTitre,
            float  montant,
            int    points,
            String reference,
            String methode,
            String aiMessage) {

        String date = LocalDate.now().format(
                DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH)
        );

        // Escape special chars for XML
        String safeMessage = escapeXml(aiMessage);
        String safeTitre   = escapeXml(projetTitre);
        String safeNom     = escapeXml(nom);
        String safeRef     = escapeXml(reference);

        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
          "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
          <title>Recu Madina</title>
          <style type="text/css">
            @page {
              size: A4;
              margin: 0;
            }
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
              font-family: Helvetica, Arial, sans-serif;
              background: #F7F4EF;
              color: #0C1F3F;
              font-size: 13px;
            }
            .page {
              width: 100%%;
              min-height: 297mm;
              background: #F7F4EF;
              padding: 32px;
            }
            .card {
              background: #ffffff;
              border-radius: 16px;
              overflow: hidden;
              max-width: 480px;
              margin: 0 auto;
            }
            /* Header */
            .header {
              background: #0C1F3F;
              padding: 36px 40px 28px;
              text-align: center;
            }
            .logo-box {
              display: inline-block;
              background: #E8532A;
              border-radius: 12px;
              width: 48px; height: 48px;
              text-align: center;
              vertical-align: middle;
            }
            .logo-letter {
              color: #ffffff;
              font-size: 26px;
              font-weight: 900;
              line-height: 48px;
            }
            .logo-name {
              display: inline-block;
              color: #ffffff;
              font-size: 26px;
              font-weight: 900;
              letter-spacing: -0.03em;
              vertical-align: middle;
              margin-left: 12px;
            }
            .header-sub {
              color: rgba(255,255,255,0.45);
              font-size: 11px;
              margin-top: 8px;
            }
            /* Green banner */
            .confirmed {
              background: #0D9B76;
              padding: 14px 40px;
              text-align: center;
              color: #ffffff;
              font-size: 14px;
              font-weight: 700;
            }
            /* Body */
            .body { padding: 32px 40px 28px; }
            /* AI message */
            .ai-box {
              background: #F0F9F6;
              border-left: 4px solid #0D9B76;
              border-radius: 0 10px 10px 0;
              padding: 14px 18px;
              margin-bottom: 24px;
            }
            .ai-text {
              font-size: 13px;
              color: #1a4a3a;
              line-height: 1.7;
              font-style: italic;
            }
            /* Project title */
            .section-label {
              font-size: 10px;
              color: #aaa;
              text-transform: uppercase;
              letter-spacing: 0.06em;
              margin-bottom: 4px;
            }
            .project-name {
              font-size: 17px;
              font-weight: 900;
              color: #0C1F3F;
              margin-bottom: 20px;
            }
            /* Details box */
            .details-box {
              background: #F7F4EF;
              border-radius: 12px;
              padding: 20px 24px;
              margin-bottom: 24px;
            }
            .detail-row {
              padding: 9px 0;
              border-bottom: 1px solid #E8E4DE;
            }
            .detail-row-last { padding: 9px 0; }
            .detail-label { font-size: 12px; color: #888; }
            .detail-value { font-size: 13px; font-weight: 700; color: #0C1F3F; }
            .detail-amount { font-size: 20px; font-weight: 900; color: #0C1F3F; }
            .detail-points { font-size: 13px; font-weight: 700; color: #F59E0B; }
            .detail-ref { font-size: 10px; color: #aaa; word-break: break-all; }
            /* Two-col row */
            .row-flex {
              width: 100%%;
              display: block;
              overflow: hidden;
            }
            .row-left  { float: left; }
            .row-right { float: right; text-align: right; }
            .clearfix { clear: both; }
            /* Impact */
            .impact {
              background: #F0F9F6;
              border: 1px solid rgba(13,155,118,0.2);
              border-radius: 10px;
              padding: 14px 20px;
              text-align: center;
              margin-bottom: 24px;
            }
            .impact-text { font-size: 12.5px; color: #0D9B76; font-weight: 600; }
            /* Footer */
            .footer {
              background: #F7F4EF;
              border-top: 1px solid #E8E4DE;
              padding: 22px 40px;
              text-align: center;
            }
            .footer-brand { font-size: 18px; font-weight: 900; color: #0C1F3F; }
            .footer-sub { font-size: 11px; color: #bbb; margin-top: 4px; }
          </style>
        </head>
        <body>
        <div class="page">
          <div class="card">

            <!-- HEADER -->
            <div class="header">
              <div>
                <span class="logo-box"><span class="logo-letter">M</span></span>
                <span class="logo-name">Madina</span>
              </div>
              <p class="header-sub">Plateforme de participation citoyenne · Tunis</p>
            </div>

            <!-- CONFIRMED BANNER -->
            <div class="confirmed">&#10003;&#160;&#160;Paiement confirme</div>

            <!-- BODY -->
            <div class="body">

              <!-- AI MESSAGE -->
              <div class="ai-box">
                <p class="ai-text">"%s"</p>
              </div>

              <!-- PROJECT -->
              <p class="section-label">Projet soutenu</p>
              <p class="project-name">%s</p>

              <!-- DETAILS -->
              <div class="details-box">

                <div class="detail-row">
                  <div class="row-flex">
                    <div class="row-left"><span class="detail-label">Donateur</span></div>
                    <div class="row-right"><span class="detail-value">%s</span></div>
                    <div class="clearfix"/>
                  </div>
                </div>

                <div class="detail-row">
                  <div class="row-flex">
                    <div class="row-left"><span class="detail-label">Montant</span></div>
                    <div class="row-right"><span class="detail-amount">%.2f DT</span></div>
                    <div class="clearfix"/>
                  </div>
                </div>

                <div class="detail-row">
                  <div class="row-flex">
                    <div class="row-left"><span class="detail-label">Points gagnes</span></div>
                    <div class="row-right"><span class="detail-points">+%d pts</span></div>
                    <div class="clearfix"/>
                  </div>
                </div>

                <div class="detail-row">
                  <div class="row-flex">
                    <div class="row-left"><span class="detail-label">Methode</span></div>
                    <div class="row-right"><span class="detail-value">%s</span></div>
                    <div class="clearfix"/>
                  </div>
                </div>

                <div class="detail-row">
                  <div class="row-flex">
                    <div class="row-left"><span class="detail-label">Date</span></div>
                    <div class="row-right"><span class="detail-value">%s</span></div>
                    <div class="clearfix"/>
                  </div>
                </div>

                <div class="detail-row-last">
                  <div class="row-flex">
                    <div class="row-left"><span class="detail-label">Reference</span></div>
                    <div class="row-right"><span class="detail-ref">%s</span></div>
                    <div class="clearfix"/>
                  </div>
                </div>

              </div>

              <!-- IMPACT -->
              <div class="impact">
                <p class="impact-text">Votre don ameliore directement la ville de Tunis.</p>
              </div>

            </div>

            <!-- FOOTER -->
            <div class="footer">
              <p class="footer-brand">Madina</p>
              <p class="footer-sub">
                Plateforme de participation citoyenne · Tunis, Tunisie<br/>
                Recu genere automatiquement
              </p>
            </div>

          </div>
        </div>
        </body>
        </html>
        """.formatted(
                safeMessage,
                safeTitre,
                safeNom,
                montant,
                points,
                methode,
                date,
                safeRef
        );
    }

    // ── XML escape helper ──────────────────────────────────
    private String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&apos;");
    }

    // ── Name extractor from email ──────────────────────────
    public static String extractName(String userId) {
        if (userId == null || userId.isBlank()) return "Citoyen";
        String local = userId.contains("@")
                ? userId.split("@")[0] : userId;
        return Arrays.stream(local.split("[._\\-]"))
                .filter(w -> !w.isBlank())
                .map(w -> Character.toUpperCase(w.charAt(0))
                        + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}