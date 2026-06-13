package tn.cityvoice.personnelservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.personnelservice.entity.Fonction;
import tn.cityvoice.personnelservice.entity.MembreEquipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembreRepository extends JpaRepository<MembreEquipe, UUID> {
    List<MembreEquipe> findMembreEquipesByFonction(Fonction fonction);
    List<MembreEquipe> findMembreEquipesByNom(String nom);

    boolean existsByUserId(UUID userId);
    Optional<MembreEquipe> findByUserId(UUID userId);

    boolean existsByEmailIgnoreCase(String email);

}
