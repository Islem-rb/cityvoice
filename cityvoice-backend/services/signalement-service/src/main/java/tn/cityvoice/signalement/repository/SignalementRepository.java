package tn.cityvoice.signalement.repository;

import tn.cityvoice.signalement.entity.Signalement;
import tn.cityvoice.signalement.enums.StatutSignalement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SignalementRepository extends JpaRepository<Signalement, Long> {


    List<Signalement> findByCitoyenIdOrderByDateSignalementDesc(String citoyenId);


    List<Signalement> findByStatutOrderByDateSignalementDesc(StatutSignalement statut);


    List<Signalement> findByEquipeIAOrderByDateSignalementDesc(String equipeIA);


    @Query(value = """
        SELECT * FROM signalements s
        WHERE (
            6371 * acos(
                LEAST(1.0, cos(radians(:lat)) * cos(radians(s.latitude))
                * cos(radians(s.longitude) - radians(:lng))
                + sin(radians(:lat)) * sin(radians(s.latitude)))
            )
        ) <= :radiusKm
        ORDER BY s.date_signalement DESC
        """, nativeQuery = true)
    List<Signalement> findByProximite(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radiusKm") double radiusKm
    );


    List<Signalement> findByStatutAndEquipeIAIsNullOrderByDateSignalementAsc(
        StatutSignalement statut
    );


    long countByStatut(StatutSignalement statut);


    @Query("SELECT s FROM Signalement s LEFT JOIN FETCH s.medias WHERE s.id = :id")
    Optional<Signalement> findByIdWithMedias(@Param("id") Long id);
}
