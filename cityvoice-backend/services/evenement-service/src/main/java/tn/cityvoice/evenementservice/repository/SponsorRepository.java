package tn.cityvoice.evenementservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.evenementservice.entity.Sponsor;

public interface SponsorRepository extends JpaRepository<Sponsor, Long> {
}