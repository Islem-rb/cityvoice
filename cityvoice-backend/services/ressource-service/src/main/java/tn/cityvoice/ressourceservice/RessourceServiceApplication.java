package tn.cityvoice.ressourceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class RessourceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RessourceServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("✅ RessourceServiceApplication demarre sur le port 8085");
        System.out.println("========================================");
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
