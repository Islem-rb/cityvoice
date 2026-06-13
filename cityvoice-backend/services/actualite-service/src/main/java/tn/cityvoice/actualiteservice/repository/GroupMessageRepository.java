package tn.cityvoice.actualiteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.cityvoice.actualiteservice.entity.GroupMessage;

import java.util.List;

@Repository
public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {

    List<GroupMessage> findByGroupIdOrderBySentAtAsc(Long groupId);

    List<GroupMessage> findByGroupIdAndIdGreaterThanOrderBySentAtAsc(Long groupId, Long lastId);
}
