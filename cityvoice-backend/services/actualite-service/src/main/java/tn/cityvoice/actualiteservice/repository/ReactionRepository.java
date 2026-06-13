package tn.cityvoice.actualiteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.actualiteservice.entity.Reaction;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    List<Reaction>     findByPost_Id(Long postId);
    Optional<Reaction> findByUserIdAndPost_Id(String userId, Long postId);
}
