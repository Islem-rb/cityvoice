package tn.cityvoice.projetservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.projetservice.entity.Projet;
import tn.cityvoice.projetservice.entity.enums.StatutProjet;
import java.util.List;

public interface ProjetRepository extends JpaRepository<Projet, Long> {
    List<Projet> findByStatut(StatutProjet statut);
    List<Projet> findByCategorie(String categorie);
    List<Projet> findByAdminId(String adminId);
}