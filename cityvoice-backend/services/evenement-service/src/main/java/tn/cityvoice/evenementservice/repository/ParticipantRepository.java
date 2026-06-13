package tn.cityvoice.evenementservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.evenementservice.entity.Participant;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    boolean existsByEvenement_IdAndCitoyenId(Long evenementId, String citoyenId);
    Optional<Participant> findByQrToken(String qrToken);
    List<Participant> findByEvenement_Id(Long evenementId);
}