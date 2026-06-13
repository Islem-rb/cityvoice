package tn.cityvoice.actualiteservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ActualiteServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActualiteServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("✅ ActualiteServiceApplication demarre sur le port 8083");
        System.out.println("========================================");
    }
}
