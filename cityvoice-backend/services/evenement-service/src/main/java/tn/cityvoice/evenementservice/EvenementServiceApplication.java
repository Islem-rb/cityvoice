package tn.cityvoice.evenementservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class EvenementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvenementServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("✅ EvenementServiceApplication demarre sur le port 8084");
        System.out.println("========================================");
    }
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
