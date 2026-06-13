package tn.cityvoice.signalement.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Stub Kafka Producer — actif quand Kafka est désactivé (test local).
 * Remplacer par la vraie implémentation avec KafkaTemplate en prod.
 */
@Component
@Slf4j
public class SignalementProducer {

    public void signalementCree(Long id, String type, Double lat, Double lng, String citoyenId) {
        log.info("[EVENT] signalement.cree → id={} type={} citoyen={}", id, type, citoyenId);
    }

    public void statutChange(Long id, String ancien, String nouveau, String  citoyenId) {
        log.info("[EVENT] signalement.statut_change → id={} {}→{}", id, ancien, nouveau);
    }

    public void equipeAffectee(Long id, String equipe, String label, Double delai) {
        log.info("[EVENT] signalement.equipe_affectee → id={} equipe={} délai={}h", id, label, delai);
    }

    public void signalementResolu(Long id, String  citoyenId, int points) {
        log.info("[EVENT] signalement.resolu → id={} citoyen={} +{}pts", id, citoyenId, points);
    }
}
