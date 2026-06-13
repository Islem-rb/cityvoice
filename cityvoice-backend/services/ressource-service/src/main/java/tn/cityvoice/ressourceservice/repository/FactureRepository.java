// tn/cityvoice/ressourceservice/repository/FactureRepository.java
package tn.cityvoice.ressourceservice.repository;

import tn.cityvoice.ressourceservice.entity.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FactureRepository extends JpaRepository<Facture, Long> {
    List<Facture> findByTechnicienId(String technicienId);
    List<Facture> findByDemandeId(Long demandeId);
    List<Facture> findByChefId(Long chefId);
    List<Facture> findByStatut(String statut);
}