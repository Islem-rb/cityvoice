package tn.cityvoice.ressourceservice.controllers;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final String IMAGE_DIR = "/home/ranim/PiFinal/cityvoice-backend/services/ressource-service/src/main/resources/static/images/";

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Object> serveImage(@PathVariable("filename") String filename) {
        try {
            Path filePath = Paths.get(IMAGE_DIR + filename);
            File file = filePath.toFile();

            System.out.println("=== DEMANDE IMAGE ===");
            System.out.println("Filename: " + filename);
            System.out.println("Path: " + filePath);
            System.out.println("File exists: " + file.exists());

            if (!file.exists()) {
                System.out.println("❌ Fichier non trouvé");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Fichier non trouvé");
            }

            if (!file.canRead()) {
                System.out.println("❌ Fichier non lisible");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Fichier non lisible");
            }

            // Déterminer le content type
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                if (filename.endsWith(".png")) {
                    contentType = "image/png";
                } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else {
                    contentType = "application/octet-stream";
                }
            }

            System.out.println("Content-Type: " + contentType);
            System.out.println("File size: " + file.length() + " bytes");

            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(file.length())
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(resource);

        } catch (IOException e) {
            System.out.println("❌ Erreur: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur: " + e.getMessage());
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("ImageController fonctionne !");
    }

    @GetMapping("/list")
    public ResponseEntity<String> listFiles() {
        try {
            Path dir = Paths.get(IMAGE_DIR);
            StringBuilder files = new StringBuilder("Fichiers disponibles:\n");
            Files.list(dir).forEach(p -> files.append(p.getFileName()).append("\n"));
            return ResponseEntity.ok(files.toString());
        } catch (IOException e) {
            return ResponseEntity.ok("Erreur: " + e.getMessage());
        }
    }
}