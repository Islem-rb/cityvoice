package tn.cityvoice.evenementservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.cityvoice.evenementservice.dto.request.QrVerificationRequest;
import tn.cityvoice.evenementservice.dto.response.QrVerificationResponse;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.entity.Participant;
import tn.cityvoice.evenementservice.entity.Participant.StatutPresence;
import tn.cityvoice.evenementservice.repository.EvenementRepository;
import tn.cityvoice.evenementservice.repository.ParticipantRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class QrCodeService {

    private final ParticipantRepository participantRepository;
    private final EvenementRepository   evenementRepository;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transactional
    public QrVerificationResponse verifier(QrVerificationRequest request) {

        // token vide
        if (request.getQrToken() == null || request.getQrToken().isBlank()) {
            return invalide("Token manquant");
        }

        //pour Chercher le participant
        return participantRepository
                .findByQrToken(request.getQrToken())
                .map(this::traiter)
                .orElse(invalide("QR Code non reconnu"));
    }

    private QrVerificationResponse traiter(Participant p) {

        // code qr déjà scanné ?
        if (p.getStatutPresence() == StatutPresence.CONFIRME) {
            String quand = p.getDatePresenceConfirmee() != null
                    ? p.getDatePresenceConfirmee().format(FMT) : "—";
            return QrVerificationResponse.builder()
                    .statut("DEJA_SCANNE")
                    .message("Déjà validé le " + quand)
                    .nomCitoyen(p.getNomCitoyen())
                    .emailCitoyen(p.getEmailCitoyen())
                    .build();
        }

        // nom de l'événement
        String nomEv = evenementRepository.findById(p.getEvenement().getId())
                .map(Evenement::getTitre)
                .orElse("Événement #" + p.getEvenement().getId());

        // marquer CONFIRME + timestamp
        p.setStatutPresence(StatutPresence.CONFIRME);
        p.setDatePresenceConfirmee(LocalDateTime.now());
        participantRepository.save(p);

        // retourner VALIDE
        return QrVerificationResponse.builder()
                .statut("VALIDE")
                .message("Entrée validée")
                .nomCitoyen(p.getNomCitoyen())
                .emailCitoyen(p.getEmailCitoyen())
                .nomEvenement(nomEv)
                .dateInscription(
                        p.getDatePresenceConfirmee() != null
                                ? p.getDatePresenceConfirmee().format(FMT) : "—")
                .build();
    }

    private QrVerificationResponse invalide(String message) {
        return QrVerificationResponse.builder()
                .statut("INVALIDE")
                .message(message)
                .build();
    }
}