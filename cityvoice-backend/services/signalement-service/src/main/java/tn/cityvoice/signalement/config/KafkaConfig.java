package tn.cityvoice.signalement.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    @Bean
    public NewTopic topicSignalementCree() {
        return TopicBuilder.name("signalement.cree").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicStatutChange() {
        return TopicBuilder.name("signalement.statut_change").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicEquipeAffectee() {
        return TopicBuilder.name("signalement.equipe_affectee").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicResolu() {
        return TopicBuilder.name("signalement.resolu").partitions(3).replicas(1).build();
    }
}
