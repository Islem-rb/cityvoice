package tn.cityvoice.evenementservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.evenementservice.entity.EvenementSponsor;
import java.util.List;

public interface EvenementSponsorRepository extends JpaRepository<EvenementSponsor, Long> {
    List<EvenementSponsor> findByEvenementId(Long evenementId);
    List<EvenementSponsor> findBySponsorId(Long sponsorId);
    void deleteByEvenementIdAndSponsorId(Long evenementId, Long sponsorId);
}