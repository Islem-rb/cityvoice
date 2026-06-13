package tn.cityvoice.userservice;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.userservice.service.BadgeService;

@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("✅ UserServiceApplication demarre sur le port 8081");
        System.out.println("========================================");
    }

    @Bean
    ApplicationRunner initBadges(BadgeService badgeService) {
        return args -> badgeService.initDefaultBadges();
    }
}
