package tn.cityvoice.personnelservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.personnelservice.entity.Equipe;

import java.util.List;
import java.util.UUID;

public interface EquipeRepository extends JpaRepository<Equipe, UUID> {
    Equipe findByName(String name);
    List<Equipe> findAllByOrderByNameAsc();
    List<Equipe> findBySpecialite(String specialite);

}
