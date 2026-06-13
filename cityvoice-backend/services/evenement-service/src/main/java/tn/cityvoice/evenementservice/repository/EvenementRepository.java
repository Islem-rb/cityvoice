package tn.cityvoice.evenementservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.evenementservice.entity.Evenement;
import tn.cityvoice.evenementservice.enums.StatutEvenement;

import java.util.List;

public interface EvenementRepository extends JpaRepository<Evenement, Long> {
    List<Evenement> findByStatutOrderByDateDebutAsc(StatutEvenement statut);
    List<Evenement> findByOrganisateurId(Long organisateurId);
}