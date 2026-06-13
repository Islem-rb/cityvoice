package tn.cityvoice.ressourceservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tn.cityvoice.ressourceservice.entity.Ressource;
import tn.cityvoice.ressourceservice.services.RessourceService;

import java.util.List;

@Component
public class FixImageUrls implements CommandLineRunner {

    @Autowired
    private RessourceService ressourceService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== CORRECTION DES URLs D'IMAGES ===");

        List<Ressource> resources = ressourceService.getAll();

        for (Ressource r : resources) {
            String oldUrl = r.getImageUrl();
            String newUrl = oldUrl;

            if (oldUrl != null) {
                // Corriger les URLs complètes
                if (oldUrl.startsWith("http://localhost:8085/")) {
                    newUrl = oldUrl.replace("http://localhost:8085", "");
                    System.out.println("Correction URL complète: " + oldUrl + " -> " + newUrl);
                }

                // Corriger l'URL "/images/grues"
                if (oldUrl.equals("/images/grues")) {
                    newUrl = "/images/grues.png";
                    System.out.println("Correction URL grues: " + oldUrl + " -> " + newUrl);
                }

                // Corriger les URLs qui contiennent "uploads"
                if (oldUrl.contains("/uploads/")) {
                    newUrl = oldUrl.replace("/uploads/", "/images/");
                    System.out.println("Correction URL uploads: " + oldUrl + " -> " + newUrl);
                }

                // Si l'URL n'a pas d'extension, ajouter .png
                if (!newUrl.contains(".") && !newUrl.endsWith("/")) {
                    newUrl = newUrl + ".png";
                    System.out.println("Ajout extension: " + oldUrl + " -> " + newUrl);
                }

                if (!newUrl.equals(oldUrl)) {
                    r.setImageUrl(newUrl);
                    ressourceService.update(r.getId(), r);
                    System.out.println("✅ Corrigé: " + r.getNom() + " - " + oldUrl + " -> " + newUrl);
                }
            }
        }

        System.out.println("=== CORRECTION TERMINÉE ===");
    }
}