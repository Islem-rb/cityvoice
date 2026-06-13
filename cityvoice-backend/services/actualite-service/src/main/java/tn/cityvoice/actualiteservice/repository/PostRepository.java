package tn.cityvoice.actualiteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.cityvoice.actualiteservice.entity.Post;

import java.util.List;
import java.util.Optional;


public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.medias WHERE p.id = :id")
    Optional<Post> findByIdWithMedias(@Param("id") Long id); //1 post + ses médias


    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.medias")
    List<Post> findAllWithMedias(); //tous les posts + leurs médias
}