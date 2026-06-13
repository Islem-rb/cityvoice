package tn.cityvoice.signalement.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Client Feign vers le personnel-service.
 * Utilisé pour retrouver l'équipe assignée par l'IA dans la base
 * et identifier le chef d'équipe associé (userId) pour lui envoyer le contrat.
 */
@FeignClient(
    name  = "personnel-service",
    url   = "${personnel.service.url:http://localhost:8086}"
)
public interface PersonnelClient {

    /** Retourne toutes les équipes avec leurs membres */
    @GetMapping("/personnel/equipe/get")
    List<EquipeDto> getAllEquipes();

    // ── DTOs internes ───────────────────────────────────────────────

    @Data
    class EquipeDto {
        private String       id;
        private String       name;
        private String       specialite;
        private String       etat;
        private List<MembreDto> membresEquipe;
    }

    @Data
    class MembreDto {
        private String id;
        private String nom;
        private String prenom;
        private String fonction;   // "CHEF_EQUIPE" | "OUVRIER_SPECIALISTE" | ...
        private String userId;     // UUID du compte utilisateur (peut être null)
    }
}
