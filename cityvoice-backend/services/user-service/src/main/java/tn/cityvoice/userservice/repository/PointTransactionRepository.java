package tn.cityvoice.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.cityvoice.userservice.entity.PointTransaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {

    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT COALESCE(SUM(p.points), 0) FROM PointTransaction p WHERE p.user.id = :userId")
    int sumPointsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PointTransaction pt WHERE pt.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(p.points), 0) FROM PointTransaction p " +
            "WHERE p.user.id = :userId " +
            "AND p.points > 0 " +
            "AND p.createdAt >= :startOfMonth")
    int sumPositivePointsSince(
            @Param("userId") UUID userId,
            @Param("startOfMonth") LocalDateTime startOfMonth
    );
}