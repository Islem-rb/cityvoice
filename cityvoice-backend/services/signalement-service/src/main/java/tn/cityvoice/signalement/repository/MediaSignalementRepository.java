package tn.cityvoice.signalement.repository;

import tn.cityvoice.signalement.entity.MediaSignalement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaSignalementRepository extends JpaRepository<MediaSignalement, Long> {
    List<MediaSignalement> findBySignalementId(Long signalementId);
}
