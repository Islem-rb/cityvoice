package tn.cityvoice.evenementservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.evenementservice.entity.RapportSponsor;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface RapportSponsorRepository extends JpaRepository<RapportSponsor, Long> {
    List<RapportSponsor> findAllByOrderByDateRapportDesc();
    Optional<RapportSponsor> findTopByOrderByDateRapportDesc();
    Optional<RapportSponsor> findByDateRapport(LocalDate date);
}