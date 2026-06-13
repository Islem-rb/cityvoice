package tn.cityvoice.evenementservice.service;
import tn.cityvoice.evenementservice.entity.EvenementNotification;
import tn.cityvoice.evenementservice.entity.EvenementNotification.TypeNotification;
import tn.cityvoice.evenementservice.repository.EvenementNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class EvenementNotificationService {

    private final EvenementNotificationRepository repository;

    // Map destinataireId → liste de SSE emitters
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public EvenementNotificationService(EvenementNotificationRepository repository) {
        this.repository = repository;
    }

    // ── Créer et envoyer une notification ─────────────
    public EvenementNotification creer(
            String destinataireId,
            String titre,
            String message,
            TypeNotification type,
            Long evenementId,
            String evenementTitre) {

        EvenementNotification notif = new EvenementNotification();
        notif.setDestinataireId(destinataireId);
        notif.setTitre(titre);
        notif.setMessage(message);
        notif.setType(type);
        notif.setEvenementId(evenementId);
        notif.setEvenementTitre(evenementTitre);

        EvenementNotification saved = repository.save(notif);

        // Envoyer en temps réel via SSE
        envoyerSSE(destinataireId, saved);

        return saved;
    }

    // Surcharge sans evenementId (pour notifs générales)
    public EvenementNotification creer(
            String destinataireId,
            String titre,
            String message,
            TypeNotification type) {
        return creer(destinataireId, titre, message, type, null, null);
    }

    // ── SSE ───────────────────────────────────────────
    public SseEmitter abonner(String destinataireId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.computeIfAbsent(destinataireId,
                k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> retirerEmitter(destinataireId, emitter));
        emitter.onTimeout(()    -> retirerEmitter(destinataireId, emitter));
        emitter.onError(e       -> retirerEmitter(destinataireId, emitter));

        // Envoyer un event "connected" pour confirmer la connexion
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connecté aux notifications"));
        } catch (IOException e) {
            retirerEmitter(destinataireId, emitter);
        }

        return emitter;
    }

    private void envoyerSSE(String destinataireId, EvenementNotification notif) {
        List<SseEmitter> liste = emitters.get(destinataireId);
        if (liste == null || liste.isEmpty()) return;

        List<SseEmitter> mortes = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : liste) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notif));
            } catch (IOException e) {
                mortes.add(emitter);
            }
        }
        liste.removeAll(mortes);
    }

    private void retirerEmitter(String destinataireId, SseEmitter emitter) {
        List<SseEmitter> liste = emitters.get(destinataireId);
        if (liste != null) liste.remove(emitter);
    }

    // ── Lecture ───────────────────────────────────────
    public List<EvenementNotification> getNotifications(String destinataireId) {
        return repository.findByDestinataireIdOrderByDateCreationDesc(destinataireId);
    }

    public long compterNonLues(String destinataireId) {
        return repository.countByDestinataireIdAndLuFalse(destinataireId);
    }

    public void marquerLue(Long id) {
        repository.findById(id).ifPresent(n -> {
            n.setLu(true);
            repository.save(n);
        });
    }

    public void marquerToutesLues(String destinataireId) {
        repository.marquerToutesLues(destinataireId);
    }
}