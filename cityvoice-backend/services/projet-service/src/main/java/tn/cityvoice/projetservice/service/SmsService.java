package tn.cityvoice.projetservice.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.messaging-service-sid}")
    private String messagingServiceSid;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
    }

    @Async
    public void sendDonationSms(String toPhone, String projetTitre,
                                float montant, int points) {
        if (toPhone == null || toPhone.isBlank()) {
            log.info("No phone provided, skipping SMS");
            return;
        }

        try {
            // Ensure phone has country code
            String phone = toPhone.startsWith("+")
                    ? toPhone : "+216" + toPhone;

            String body = String.format(
                    "✅ Madina\n" +
                            "Don confirmé : %.0f DT\n" +
                            "Projet : %s\n" +
                            "Points gagnés : +%d ⭐\n" +
                            "Merci pour votre contribution!",
                    montant, projetTitre, points
            );

            Message.creator(
                    new PhoneNumber(phone),
                    messagingServiceSid,
                    body
            ).create();

            log.info("SMS sent to {}", phone);

        } catch (Exception e) {
            log.error("SMS error to {}: {}", toPhone, e.getMessage());
        }
    }
}