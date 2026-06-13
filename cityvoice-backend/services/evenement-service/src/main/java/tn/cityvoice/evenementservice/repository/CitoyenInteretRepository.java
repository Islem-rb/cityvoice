package tn.cityvoice.evenementservice.repository;

import tn.cityvoice.evenementservice.entity.CitoyenInteret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CitoyenInteretRepository extends JpaRepository<CitoyenInteret, Long> {

    List<CitoyenInteret> findByCitoyenId(String citoyenId);

    Optional<CitoyenInteret> findByCitoyenIdAndEvenementId(String citoyenId, Long evenementId);

    boolean existsByCitoyenIdAndEvenementId(String citoyenId, Long evenementId);

    void deleteByCitoyenIdAndEvenementId(String citoyenId, Long evenementId);

    // Types les plus likés par un citoyen
    @Query("SELECT c.typeEvenement, COUNT(c) as cnt " +
            "FROM CitoyenInteret c WHERE c.citoyenId = :citoyenId " +
            "GROUP BY c.typeEvenement ORDER BY cnt DESC")
    List<Object[]> findTopTypesByCitoyenId(String citoyenId);

    // IDs des événements likés
    @Query("SELECT c.evenementId FROM CitoyenInteret c WHERE c.citoyenId = :citoyenId")
    List<Long> findEvenementIdsByCitoyenId(String citoyenId);
}