package tn.cityvoice.evenementservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.evenementservice.entity.Suggestion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.cityvoice.evenementservice.entity.Suggestion;
import java.util.List;
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {
    List<Suggestion> findByStatut(String statut);
    List<Suggestion> findByCitoyenId(String citoyenId);
}