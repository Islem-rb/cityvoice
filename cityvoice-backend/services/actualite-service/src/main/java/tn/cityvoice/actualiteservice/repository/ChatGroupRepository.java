package tn.cityvoice.actualiteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.cityvoice.actualiteservice.entity.ChatGroup;

import java.util.List;

@Repository
public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {

    /**
     * Trouve tous les groupes dont memberIds contient l'userId donné.
     * Utilise LIKE '%userId%' — suffisant en développement.
     */
    @Query("SELECT g FROM ChatGroup g WHERE g.memberIds LIKE %:userId%")
    List<ChatGroup> findGroupsForUser(@Param("userId") String userId);
}
