package tn.cityvoice.evenementservice.service;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.entity.Participant;

import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class WhatsAppService {

    @Value("${twilio.whatsapp-from}")
    private String from;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    /**
     * Envoie un rappel WhatsApp au participant 24h avant l'événement.
     * Ne fait rien si telCitoyen est null ou vide.
     */
    public void envoyerRappelWhatsApp(Evenement ev, Participant p) {
        if (p.getTelCitoyen() == null || p.getTelCitoyen().isBlank()) {
            log.warn("⚠️ Pas de téléphone pour {} — WhatsApp ignoré", p.getNomCitoyen());
            return;
        }

        try {
            String to  = "whatsapp:" + p.getTelCitoyen(); // ex: whatsapp:+21612345678
            String texte = construireMessage(ev, p);

            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(from),
                    texte
            ).create();

            log.info("✅ WhatsApp envoyé à {} — SID: {}", p.getTelCitoyen(), message.getSid());

        } catch (Exception e) {
            log.error("❌ Erreur envoi WhatsApp à {} : {}", p.getTelCitoyen(), e.getMessage());
        }
    }

    private String construireMessage(Evenement ev, Participant p) {
        String date = ev.getDateDebut() != null
                ? ev.getDateDebut().format(FMT)
                : "—";

        return String.format(
                "⏰ *Rappel MADINA*\n\n" +
                        "Bonjour *%s* 👋\n\n" +
                        "Votre événement commence *demain* !\n\n" +
                        "📌 *%s*\n" +
                        "📍 %s\n" +
                        "🗓 %s\n\n" +
                        "%s\n\n" +
                        "_N'oubliez pas votre QR Code d'entrée !_\n" +
                        "— Équipe MADINA 🌍",
                p.getNomCitoyen(),
                ev.getTitre(),
                ev.getLieu(),
                date,
                Boolean.TRUE.equals(ev.getEstPayant())
                        ? "💰 Prix : " + ev.getPrix() + " TND"
                        : "✅ Entrée gratuite"
        );
    }
}