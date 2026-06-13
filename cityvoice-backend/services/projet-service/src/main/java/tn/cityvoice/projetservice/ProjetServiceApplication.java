package tn.cityvoice.projetservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ProjetServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjetServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("✅ ProjetServiceApplication demarre sur le port 8087");
        System.out.println("========================================");
    }
}
