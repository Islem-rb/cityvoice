package tn.cityvoice.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("✅ GatewayServiceApplication demarre sur le port 8080");
        System.out.println("========================================");
    }
}
