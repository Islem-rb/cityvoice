package tn.cityvoice.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.cityvoice.userservice.entity.UserBadge;

import java.util.List;
import java.util.UUID;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {
    List<UserBadge> findByUserIdOrderByObtainedAtDesc(UUID userId);
    boolean existsByUserIdAndBadgeCode(UUID userId, String badgeCode);

    @Modifying
    @Query("DELETE FROM UserBadge ub WHERE ub.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}