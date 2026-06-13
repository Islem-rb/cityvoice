package tn.cityvoice.actualiteservice.live;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiveCommentRepository extends JpaRepository<LiveComment, Long> {

    /** Tous les commentaires d'une room, du plus ancien au plus récent. */
    List<LiveComment> findByRoomNameOrderByDateAsc(String roomName);

    /** Supprime tous les commentaires d'une room (appelé quand le live se termine). */
    long deleteByRoomName(String roomName);
}
