package tn.cityvoice.signalement.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO représentant une équipe terrain.
 * Ces données sont statiques pour l'instant (mock) et reflètent
 * les équipes définies dans le service IA (team_assignment.py).
 *
 * Architecture scalable : remplacer la liste statique dans EquipeController
 * par un appel à un microservice de gestion d'équipes sans changer ce DTO.
 */
@Data
@Builder
public class EquipeDto {

    private String       id;
    private String       label;
    private String       domaine;
    private List<String> specialites;
    private boolean      disponible;
    private int          capacite;
    private double       delaiBaseHeures;
    private String       contact;
    private String       couleur;
}
