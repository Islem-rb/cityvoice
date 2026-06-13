package tn.cityvoice.userservice.service;

import tn.cityvoice.userservice.entity.PointTransaction;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.PointReason;

import java.util.List;
import java.util.UUID;

public interface PointService {

    PointTransaction addPoints(User user, int points, PointReason reason, String description);

    void rewardInscription(User user);
    void rewardProfilComplete(User user);
    void rewardPhotoAjoutee(User user);
    void rewardPremiereConnexion(User user);
    public void addPoints(UUID userId, int points, String reason);
    List<PointTransaction> getTransactions(UUID userId);

    boolean hasReason(UUID userId, PointReason reason);
}