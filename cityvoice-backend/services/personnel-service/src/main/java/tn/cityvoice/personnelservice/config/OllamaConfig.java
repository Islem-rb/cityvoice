package tn.cityvoice.personnelservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OllamaConfig {

    @Bean(name = "ollamaRestTemplate")
    public RestTemplate ollamaRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 2 minutes pour le connect (chargement du modèle en mémoire)
        factory.setConnectTimeout(120_000);
        // 5 minutes pour la réponse (génération LLM peut être lente)
        factory.setReadTimeout(300_000);
        return new RestTemplate(factory);
    }
}