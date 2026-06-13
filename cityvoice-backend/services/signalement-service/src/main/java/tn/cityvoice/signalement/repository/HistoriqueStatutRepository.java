package tn.cityvoice.signalement.repository;

import tn.cityvoice.signalement.entity.HistoriqueStatut;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoriqueStatutRepository extends JpaRepository<HistoriqueStatut, Long> {}
