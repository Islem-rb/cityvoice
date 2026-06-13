package tn.cityvoice.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    String frontendUrl;

    @Value("${spring.mail.username}")
    String fromEmail;

    // Reset email
    public void sendResetEmail(String toEmail, String token, String nom) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("CityVoice — Réinitialisation de votre mot de passe");

            String resetLink = frontendUrl + "/auth/reset-password?token=" + token;

            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family:'Helvetica Neue',Arial,sans-serif;background:#F7F4EF;margin:0;padding:40px 0">
                  <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:16px;overflow:hidden;border:1px solid rgba(12,31,63,.08)">
                    
                    <!-- Header -->
                    <div style="background:#0C1F3F;padding:32px;text-align:center">
                      <div style="width:44px;height:44px;background:#E8532A;border-radius:12px;display:inline-flex;align-items:center;justify-content:center;margin-bottom:12px">
                        <span style="color:white;font-size:20px">📍</span>
                      </div>
                      <h1 style="color:white;font-size:22px;margin:0;font-weight:700">CityVoice</h1>
                    </div>

                    <!-- Body -->
                    <div style="padding:36px 32px">
                      <h2 style="color:#0C1F3F;font-size:20px;margin:0 0 8px">Bonjour %s,</h2>
                      <p style="color:#8888A8;font-size:14px;line-height:1.7;margin:0 0 28px">
                        Vous avez demandé la réinitialisation de votre mot de passe.<br>
                        Cliquez sur le bouton ci-dessous pour choisir un nouveau mot de passe.
                      </p>

                      <div style="text-align:center;margin-bottom:28px">
                        <a href="%s"
                           style="background:#E8532A;color:white;text-decoration:none;padding:14px 36px;border-radius:50px;font-size:15px;font-weight:700;display:inline-block">
                          Réinitialiser mon mot de passe
                        </a>
                      </div>

                      <div style="background:#F7F4EF;border-radius:10px;padding:14px 16px;margin-bottom:24px">
                        <p style="color:#8888A8;font-size:12px;margin:0">
                          Ce lien expire dans <strong style="color:#0C1F3F">30 minutes</strong>.<br>
                          Si vous n'avez pas demandé cette réinitialisation, ignorez cet email.
                        </p>
                      </div>

                      <p style="color:#C8C8D8;font-size:11px;text-align:center;margin:0">
                        CityVoice — La voix des citoyens pour une ville meilleure
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(nom, resetLink);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Erreur envoi email : " + e.getMessage());
        }
    }

    // Email verification :
    public void sendVerificationEmail(String toEmail, String token, String nom) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("CityVoice — Confirmez votre adresse email");

            String verifyLink = frontendUrl + "/auth/verify-email?token=" + token;

            String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family:'Helvetica Neue',Arial,sans-serif;background:#F7F4EF;margin:0;padding:40px 0">
              <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:16px;overflow:hidden;border:1px solid rgba(12,31,63,.08)">
                <div style="background:#0C1F3F;padding:32px;text-align:center">
                  <div style="width:44px;height:44px;background:#E8532A;border-radius:12px;display:inline-flex;align-items:center;justify-content:center;margin-bottom:12px">
                    <span style="color:white;font-size:20px">📍</span>
                  </div>
                  <h1 style="color:white;font-size:22px;margin:0;font-weight:700">CityVoice</h1>
                </div>
                <div style="padding:36px 32px">
                  <h2 style="color:#0C1F3F;font-size:20px;margin:0 0 8px">Bonjour %s 👋</h2>
                  <p style="color:#8888A8;font-size:14px;line-height:1.7;margin:0 0 28px">
                    Bienvenue sur CityVoice ! Confirmez votre adresse email pour activer votre compte.
                  </p>
                  <div style="text-align:center;margin-bottom:28px">
                    <a href="%s"
                       style="background:#E8532A;color:white;text-decoration:none;padding:14px 36px;border-radius:50px;font-size:15px;font-weight:700;display:inline-block">
                      ✓ Confirmer mon email
                    </a>
                  </div>
                  <div style="background:#F7F4EF;border-radius:10px;padding:14px 16px;margin-bottom:24px">
                    <p style="color:#8888A8;font-size:12px;margin:0">
                      Ce lien expire dans <strong style="color:#0C1F3F">24 heures</strong>.<br>
                      Si vous n'avez pas créé ce compte, ignorez cet email.
                    </p>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(nom, verifyLink);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Erreur envoi email : " + e.getMessage());
        }
    }
}