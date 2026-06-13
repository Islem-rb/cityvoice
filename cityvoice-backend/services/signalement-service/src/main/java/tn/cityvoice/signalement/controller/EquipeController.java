package tn.cityvoice.signalement.controller;

import tn.cityvoice.signalement.client.PersonnelClient;
import tn.cityvoice.signalement.dto.EquipeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Expose les équipes terrain disponibles.
 *
 * Les données sont chargées EN TEMPS RÉEL depuis le personnel-service :
 * l'IA et le frontend voient toujours la disponibilité réelle (LIBRE/OCCUPE).
 * En cas d'indisponibilité du personnel-service, un catalogue statique
 * de secours est retourné (toutes équipes marquées disponibles).
 */
@RestController
@RequestMapping("/api/v1/equipes")
@RequiredArgsConstructor
@Slf4j
public class EquipeController {

    private final PersonnelClient personnelClient;

    // ── Catalogue statique de secours ────────────────────────────────────────
    // Utilisé uniquement si le personnel-service est inaccessible.
    // Les IDs correspondent aux specialite de la base (normalisés).
    private static final List<EquipeDto> EQUIPES_FALLBACK = List.of(

        EquipeDto.builder()
            .id("voirie")
            .label("Équipe Voirie")
            .domaine("Voirie & Infrastructure")
            .specialites(List.of("trou_chaussee", "signalisation_manquante", "caniveau_bouche"))
            .disponible(true).capacite(6).delaiBaseHeures(48.0)
            .contact("voirie@cityvoice.tn").couleur("#F59E0B")
            .build(),

        EquipeDto.builder()
            .id("eclairage_public")
            .label("Équipe Éclairage Public")
            .domaine("Réseau Électrique & Éclairage")
            .specialites(List.of("lampadaire_casse", "poteau_endommage"))
            .disponible(true).capacite(4).delaiBaseHeures(24.0)
            .contact("eclairage@cityvoice.tn").couleur("#EAB308")
            .build(),

        EquipeDto.builder()
            .id("plomberie")
            .label("Équipe Plomberie")
            .domaine("Réseau Hydraulique")
            .specialites(List.of("fuite_eau", "caniveau_bouche"))
            .disponible(true).capacite(5).delaiBaseHeures(12.0)
            .contact("plomberie@cityvoice.tn").couleur("#3B82F6")
            .build(),

        EquipeDto.builder()
            .id("proprete")
            .label("Équipe Propreté")
            .domaine("Collecte & Hygiène Urbaine")
            .specialites(List.of("dechets_non_collectes"))
            .disponible(true).capacite(8).delaiBaseHeures(24.0)
            .contact("proprete@cityvoice.tn").couleur("#10B981")
            .build(),

        EquipeDto.builder()
            .id("eau_assainissement")
            .label("Équipe Eau & Assainissement")
            .domaine("Réseau d'Assainissement")
            .specialites(List.of("caniveau_bouche", "fuite_eau"))
            .disponible(true).capacite(4).delaiBaseHeures(36.0)
            .contact("assainissement@cityvoice.tn").couleur("#8B5CF6")
            .build(),

        EquipeDto.builder()
            .id("espaces_verts")
            .label("Équipe Espaces Verts")
            .domaine("Environnement & Végétation")
            .specialites(List.of("espace_vert_degrade"))
            .disponible(true).capacite(5).delaiBaseHeures(72.0)
            .contact("espaces.verts@cityvoice.tn").couleur("#22C55E")
            .build(),

        EquipeDto.builder()
            .id("infrastructure")
            .label("Équipe Infrastructure")
            .domaine("Travaux Publics")
            .specialites(List.of("trou_chaussee", "caniveau_bouche"))
            .disponible(true).capacite(6).delaiBaseHeures(48.0)
            .contact("infra@cityvoice.tn").couleur("#6B7280")
            .build(),

        EquipeDto.builder()
            .id("electricite")
            .label("Équipe Électricité")
            .domaine("Réseau Électrique")
            .specialites(List.of("lampadaire_casse", "poteau_endommage"))
            .disponible(true).capacite(4).delaiBaseHeures(24.0)
            .contact("electricite@cityvoice.tn").couleur("#FBBF24")
            .build()
    );

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Liste toutes les équipes avec leur disponibilité EN TEMPS RÉEL.
     * Source : personnel-service (base de données réelle).
     * Fallback : catalogue statique si personnel-service inaccessible.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        try {
            List<PersonnelClient.EquipeDto> equipes = personnelClient.getAllEquipes();
            if (equipes == null || equipes.isEmpty()) {
                log.warn("[EQUIPES] Personnel-service a renvoyé une liste vide — fallback statique");
                return fallbackResponse();
            }
            List<EquipeDto> result = equipes.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
            long nbLibres = result.stream().filter(EquipeDto::isDisponible).count();
            log.info("[EQUIPES] {} équipes depuis personnel-service ({} libres)", result.size(), nbLibres);
            return ResponseEntity.ok(Map.of("equipes", result, "total", result.size()));
        } catch (Exception ex) {
            log.warn("[EQUIPES] Personnel-service inaccessible — fallback statique utilisé : {}", ex.getMessage());
            return fallbackResponse();
        }
    }

    /** Retourne une équipe par son identifiant (code normalisé = specialite). */
    @GetMapping("/{id}")
    public ResponseEntity<EquipeDto> getById(@PathVariable String id) {
        try {
            List<PersonnelClient.EquipeDto> equipes = personnelClient.getAllEquipes();
            if (equipes != null) {
                return equipes.stream()
                    .filter(e -> norm(id).equalsIgnoreCase(norm(e.getSpecialite())))
                    .findFirst()
                    .map(e -> ResponseEntity.ok(toDto(e)))
                    .orElse(ResponseEntity.notFound().build());
            }
        } catch (Exception ex) {
            log.warn("[EQUIPES] getById fallback pour '{}' : {}", id, ex.getMessage());
        }
        // Fallback statique
        return EQUIPES_FALLBACK.stream()
            .filter(e -> norm(id).equalsIgnoreCase(e.getId()))
            .findFirst()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Convertit un EquipeDto du personnel-service en EquipeDto du signalement-service.
     * L'id est la spécialité normalisée pour correspondre exactement aux codes de l'IA.
     */
    private EquipeDto toDto(PersonnelClient.EquipeDto e) {
        String specialiteNorm = norm(e.getSpecialite());
        boolean libre         = "LIBRE".equalsIgnoreCase(e.getEtat());
        int     nbMembres     = e.getMembresEquipe() != null ? e.getMembresEquipe().size() : 0;

        return EquipeDto.builder()
            .id(specialiteNorm)                               // code normalisé → clé IA
            .label(e.getName() != null ? e.getName() : specialiteNorm)
            .domaine(e.getName() != null ? e.getName() : specialiteNorm)
            .specialites(List.of(specialiteNorm))
            .disponible(libre)                                // temps réel depuis DB
            .capacite(nbMembres)
            .delaiBaseHeures(24.0)                            // valeur par défaut
            .contact(specialiteNorm + "@cityvoice.tn")
            .build();
    }

    private ResponseEntity<Map<String, Object>> fallbackResponse() {
        return ResponseEntity.ok(Map.of(
            "equipes", EQUIPES_FALLBACK,
            "total",   EQUIPES_FALLBACK.size()
        ));
    }

    /** Normalise une spécialité : accents, casse, espaces → "eau_assainissement". */
    private static String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[\\s_-]+", "_")
            .replaceAll("[^a-z0-9_]", "");
    }
}
