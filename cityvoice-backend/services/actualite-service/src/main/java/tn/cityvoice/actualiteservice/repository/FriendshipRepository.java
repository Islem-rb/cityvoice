package tn.cityvoice.actualiteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.cityvoice.actualiteservice.entity.Friendship;
import tn.cityvoice.actualiteservice.entity.Friendship.FriendshipStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // Trouver la relation entre deux users (dans n'importe quel sens)
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requesterId = :u1 AND f.addresseeId = :u2) OR " +
           "(f.requesterId = :u2 AND f.addresseeId = :u1)")
    Optional<Friendship> findBetween(@Param("u1") String u1, @Param("u2") String u2);

    // Amis acceptés d'un user (exclut les bloqués)
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requesterId = :userId OR f.addresseeId = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findFriends(@Param("userId") String userId);

    // Utilisateurs bloqués par un user (il est toujours le requesterId pour un blocage)
    @Query("SELECT f FROM Friendship f WHERE f.requesterId = :userId AND f.status = 'BLOCKED'")
    List<Friendship> findBlockedBy(@Param("userId") String userId);

    // Demandes en attente reçues
    @Query("SELECT f FROM Friendship f WHERE f.addresseeId = :userId AND f.status = 'PENDING'")
    List<Friendship> findPendingReceived(@Param("userId") String userId);

    // Demandes en attente envoyées
    @Query("SELECT f FROM Friendship f WHERE f.requesterId = :userId AND f.status = 'PENDING'")
    List<Friendship> findPendingSent(@Param("userId") String userId);

    // Nombre de demandes en attente reçues
    @Query("SELECT COUNT(f) FROM Friendship f WHERE f.addresseeId = :userId AND f.status = 'PENDING'")
    long countPendingReceived(@Param("userId") String userId);

    // Mettre à jour le statut
    @Modifying
    @Query("UPDATE Friendship f SET f.status = :status WHERE " +
           "(f.requesterId = :u1 AND f.addresseeId = :u2) OR " +
           "(f.requesterId = :u2 AND f.addresseeId = :u1)")
    void updateStatus(@Param("u1") String u1, @Param("u2") String u2,
                      @Param("status") FriendshipStatus status);
}
