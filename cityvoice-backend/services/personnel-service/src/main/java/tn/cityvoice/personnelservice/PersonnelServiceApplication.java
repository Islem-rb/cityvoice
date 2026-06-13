package tn.cityvoice.personnelservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "tn.cityvoice.personnelservice.feign")
public class PersonnelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonnelServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("✅ PersonnelServiceApplication demarre sur le port 8086");
        System.out.println("========================================");
    }
}
