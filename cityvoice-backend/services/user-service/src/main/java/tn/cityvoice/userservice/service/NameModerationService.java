package tn.cityvoice.userservice.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NameModerationService {

    private List<String> blockedTerms = new ArrayList<>();

    @PostConstruct
    void loadBlockedTerms() {
        try {
            var resource = new ClassPathResource("blocked-terms.txt");
            var reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    blockedTerms.add(line);
                }
            }
            reader.close();
            System.out.println("Loaded " + blockedTerms.size() + " blocked terms");
        } catch (Exception e) {
            System.out.println("No blocked-terms.txt found — all names will pass");
        }
    }

    public record ScreeningResult(boolean appropriate, String reason) {}

    /**
     * Simple blacklist check — NO LLM
     * If word found in blacklist → rejected
     * Otherwise → accepted
     */
    public ScreeningResult screen(String name) {
        if (name == null || name.isBlank()) {
            return new ScreeningResult(true, null);
        }

        String normalized = name.toLowerCase().trim();

        for (String term : blockedTerms) {
            if (normalized.contains(term)) {
                return new ScreeningResult(false, "Nom inapproprié pour la plateforme");
            }
        }

        // Not in blacklist → ACCEPT
        System.out.println("NAME ACCEPTED: \"" + name + "\"");
        return new ScreeningResult(true, null);
    }
}