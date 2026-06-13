package tn.cityvoice.actualiteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.actualiteservice.entity.Commentaire;
import java.util.List;

public interface CommentaireRepository extends JpaRepository<Commentaire, Long> {

    List<Commentaire> findByPostId(Long postId);
}