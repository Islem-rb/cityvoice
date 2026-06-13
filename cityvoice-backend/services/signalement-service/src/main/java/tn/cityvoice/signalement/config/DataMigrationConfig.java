package tn.cityvoice.signalement.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migration de schéma SQL exécutée au démarrage.
 * Hibernate ddl-auto=update ne modifie JAMAIS le type d'une colonne existante.
 * Ce bean corrige ça de manière idempotente (sans erreur si déjà appliqué).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataMigrationConfig {

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void migrate() {

        // ── medias_signalement.url : VARCHAR(255) → LONGTEXT ──────────────────
        // Nécessaire pour stocker les images base64 (peuvent dépasser 500 000 chars)
        try {
            jdbc.execute(
                "ALTER TABLE medias_signalement MODIFY COLUMN url LONGTEXT NOT NULL"
            );
            log.info("[MIGRATION] medias_signalement.url → LONGTEXT ✓");
        } catch (Exception e) {
            // Déjà LONGTEXT ou table inexistante — ignoré
            log.debug("[MIGRATION] medias_signalement.url : {}", e.getMessage());
        }

        // ── historique_statuts.ancien_statut : nullable ───────────────────────
        // Permet d'enregistrer l'entrée initiale (pas d'ancien statut à la création)
        try {
            jdbc.execute(
                "ALTER TABLE historique_statuts MODIFY COLUMN ancien_statut VARCHAR(50) NULL"
            );
            log.info("[MIGRATION] historique_statuts.ancien_statut → nullable ✓");
        } catch (Exception e) {
            log.debug("[MIGRATION] historique_statuts.ancien_statut : {}", e.getMessage());
        }
    }
}
