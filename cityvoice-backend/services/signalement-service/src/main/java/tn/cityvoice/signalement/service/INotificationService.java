package tn.cityvoice.signalement.service;

import tn.cityvoice.signalement.entity.Notification;
import tn.cityvoice.signalement.enums.NotificationType;

import java.util.List;

public interface INotificationService {

    /** Créer + persister + pousser via WebSocket */
    Notification envoyer(String destinataireId, NotificationType type,
                         String message, String lien, Long entiteId);

    /** Toutes les notifs d'un utilisateur (pour polling) */
    List<Notification> getByUser(String userId);

    /** Notifs non lues */
    List<Notification> getNonLues(String userId);

    /** Nombre de non lues */
    long countNonLues(String userId);

    /** Marquer une notif comme lue */
    Notification marquerLue(Long notifId);

    /** Marquer toutes comme lues */
    void marquerToutesLues(String userId);
}
