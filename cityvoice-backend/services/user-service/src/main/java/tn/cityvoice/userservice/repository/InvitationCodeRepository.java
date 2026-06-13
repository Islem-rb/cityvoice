package tn.cityvoice.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.cityvoice.userservice.entity.InvitationCode;

import java.util.Optional;
import java.util.UUID;

public interface InvitationCodeRepository extends JpaRepository<InvitationCode, UUID> {
    Optional<InvitationCode> findByCode(String code);

    @Modifying
    @Query("UPDATE InvitationCode ic SET ic.usedByUser = null WHERE ic.usedByUser.id = :userId")
    void clearUsedByUser(@Param("userId") UUID userId);
}