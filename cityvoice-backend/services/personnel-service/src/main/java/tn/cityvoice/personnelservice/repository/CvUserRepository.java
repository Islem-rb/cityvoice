package tn.cityvoice.personnelservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.personnelservice.entity.CvUser;

import java.util.Optional;
import java.util.UUID;

public interface CvUserRepository extends JpaRepository<CvUser, UUID> {
    boolean existsByCandidature_IdAndUserId(UUID candidatureId, UUID userId);

    Optional<CvUser> findByCandidature_IdAndUserId(UUID candidatureId, UUID userId);
}
