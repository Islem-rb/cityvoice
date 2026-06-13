package tn.cityvoice.userservice.service;

import tn.cityvoice.userservice.entity.Badge;
import tn.cityvoice.userservice.entity.UserBadge;
import tn.cityvoice.userservice.entity.User;

import java.util.List;
import java.util.UUID;

public interface BadgeService {
    void initDefaultBadges();
    UserBadge awardBadge(User user, String badgeCode);
    void checkAndAwardBadges(User user);
    List<UserBadge> getUserBadges(UUID userId);
    List<Badge> getAllBadges();
    boolean hasBadge(UUID userId, String badgeCode);
}