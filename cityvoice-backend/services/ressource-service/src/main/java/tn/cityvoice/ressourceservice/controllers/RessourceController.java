package tn.cityvoice.ressourceservice.controllers;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import tn.cityvoice.ressourceservice.entity.MaintenanceLog;
import tn.cityvoice.ressourceservice.entity.Ressource;
import tn.cityvoice.ressourceservice.services.MaintenanceLogService;
import tn.cityvoice.ressourceservice.services.RessourceService;
import tn.cityvoice.ressourceservice.services.MaintenanceLogService;
import tn.cityvoice.ressourceservice.dto.ResourceUpdateDTO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/ressources")
public class RessourceController {

    private final RessourceService service;
    private final MaintenanceLogService maintenanceService;
    private final RestTemplate restTemplate;


    public RessourceController(RessourceService service, MaintenanceLogService maintenanceService, RestTemplate restTemplate) {
        this.service = service;
        this.maintenanceService = maintenanceService;
        this.restTemplate = restTemplate;

    }

    // GET all
    @GetMapping
    public ResponseEntity<List<Ressource>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // GET by ID
    @GetMapping("/{id}")
    public ResponseEntity<Ressource> getById(@PathVariable("id") Long id) {
        return service.getById(id)
                .map(res -> ResponseEntity.ok(res))
                .orElse(ResponseEntity.notFound().build());
    }

    // POST create
    @PostMapping
    public ResponseEntity<Ressource> create(
            @RequestParam("nom") String nom,
            @RequestParam("type") String type,
            @RequestParam("etat") String etat,
            @RequestParam("valeur") double valeur,
            @RequestParam("dureeVieEstimee") int dureeVie,
            @RequestParam("dateAchat") String dateAchat,  // ← String
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {

        Ressource ressource = new Ressource();
        ressource.setNom(nom);
        ressource.setType(type);
        ressource.setEtat(etat);
        ressource.setValeur(valeur);
        ressource.setDureeVieEstimee(dureeVie);
        ressource.setDateAchat(dateAchat);

        if (file != null && !file.isEmpty()) {
            String uploadDir = "/home/ranim/PiFinal/cityvoice-backend/services/ressource-service/src/main/resources/static/images/";            Path uploadPath = Paths.get(uploadDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 🔥 Utiliser le nom original mais remplacer les espaces
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename().replace(" ", "_");
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("✅ Fichier sauvegardé: " + filePath);

            ressource.setImageUrl("/api/images/" + fileName);
        }

        Ressource saved = service.create(ressource);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    // PUT update
    // PUT update
    // PUT update - Version corrigée (sans @RequestBody)
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Ressource> updateResource(
            @PathVariable("id") Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("nom") String nom,
            @RequestParam("type") String type,
            @RequestParam("etat") String etat,
            @RequestParam("valeur") double valeur,
            @RequestParam("dureeVieEstimee") int dureeVie,
            @RequestParam("dateAchat") String dateAchat) throws IOException {

        System.out.println("=== UPDATE RESSOURCE ===");
        System.out.println("ID: " + id);
        System.out.println("Nom: " + nom);
        System.out.println("Type: " + type);
        System.out.println("Etat: " + etat);
        System.out.println("Valeur: " + valeur);
        System.out.println("Duree vie: " + dureeVie);
        System.out.println("Date achat: " + dateAchat);
        System.out.println("Fichier: " + (file != null ? file.getOriginalFilename() : "null"));

        // Récupérer la ressource existante
        Ressource existing = service.getById(id)
                .orElseThrow(() -> new RuntimeException("Ressource introuvable avec id: " + id));

        // Mettre à jour les champs
        existing.setNom(nom);
        existing.setType(type);
        existing.setEtat(etat);
        existing.setValeur(valeur);
        existing.setDureeVieEstimee(dureeVie);
        existing.setDateAchat(dateAchat);

        // Gérer l'image si présente
        if (file != null && !file.isEmpty()) {
            String uploadDir = "/home/ranim/PiFinal/cityvoice-backend/services/ressource-service/src/main/resources/static/images/";
            Path uploadPath = Paths.get(uploadDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename().replace(" ", "_");
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            existing.setImageUrl("/api/images/" + fileName);
            System.out.println("✅ Nouvelle image sauvegardée: " + fileName);
        }

        Ressource updated = service.update(id, existing);
        System.out.println("✅ Ressource mise à jour avec succès");
        return ResponseEntity.ok(updated);
    }
    // DELETE
    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 🔹 Upload image
    @PostMapping("/upload")
    public ResponseEntity<String> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Fichier vide");
            }

            String uploadDir = System.getProperty("user.dir") + "/services/ressource-service/src/main/resources/static/images/";
            Path uploadPath = Paths.get(uploadDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String fileName = System.currentTimeMillis() + "_" + originalFilename;
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 🔑 Retourner l'URL via le nouveau endpoint
            String imageUrl = "/api/images/" + fileName;
            System.out.println("✅ Upload image: " + imageUrl);
            return ResponseEntity.ok(imageUrl);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur");
        }
    }





    private double calculerAge(Ressource resource) {
        if (resource.getDateAchat() == null) return 0;
        try {
            LocalDate dateAchat = LocalDate.parse(resource.getDateAchat());
            return ChronoUnit.YEARS.between(dateAchat, LocalDate.now());
        } catch (Exception e) {
            return 0;
        }
    }


    @GetMapping("/{id}/predict-panne")
    public ResponseEntity<?> predictPanne(@PathVariable Long id) {
        Ressource ressource = service.getById(id)
                .orElseThrow(() -> new RuntimeException("Ressource non trouvée"));

        // Calculer l'âge
        double ageAns = calculerAge(ressource);
        double pourcentageVie = (ageAns / ressource.getDureeVieEstimee()) * 100;
        if (pourcentageVie > 100) pourcentageVie = 100;

        // Compter les maintenances
        int nbMaintenances = maintenanceService.countByRessourceId(id);

        // Créer la requête pour l'API Python
        Map<String, Object> request = new HashMap<>();
        request.put("age_ans", ageAns);
        request.put("duree_vie", (double) ressource.getDureeVieEstimee());
        request.put("pourcentage_vie", pourcentageVie);
        request.put("nb_maintenances", nbMaintenances);
        request.put("valeur", ressource.getValeur());
        request.put("etat", ressource.getEtat());
        request.put("type_ressource", ressource.getType());

        // Appeler l'API Python
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:5001/predict";

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Fallback si l'API Python n'est pas disponible
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("success", true);
            fallback.put("risque", pourcentageVie);
            fallback.put("niveau", pourcentageVie >= 80 ? "Critique" : pourcentageVie >= 60 ? "Élevé" : "Moyen");
            fallback.put("couleur", pourcentageVie >= 80 ? "#ef4444" : pourcentageVie >= 60 ? "#f97316" : "#f59e0b");
            fallback.put("icone", "⚠️");
            fallback.put("jours_restants", (int) ((100 - pourcentageVie) * 3.65));
            fallback.put("recommandation", pourcentageVie >= 80 ? "REMPLACEMENT IMMÉDIAT" : "MAINTENANCE PRÉVENTIVE");
            fallback.put("confiance", 0.8);
            return ResponseEntity.ok(fallback);
        }
    }






    @PostMapping("/chatbot-gemini")
    public ResponseEntity<?> chatbotGemini(@RequestBody Map<String, String> request) {
        try {
            String question = request.get("question");
            String apiKey = "AIzaSyCXL4uABd-dQOzXueNCjRaTtm55MYxqtXU"; // Mets ta clé ici

            // Récupérer les données
            List<Ressource> resources = service.getAll();
            List<MaintenanceLog> maintenances = maintenanceService.getAll();

            String contexte = genererContexte(resources, maintenances);

            // Appeler l'API Gemini
            String reponse = appelerGemini(question, contexte, apiKey);

            return ResponseEntity.ok(Map.of("reponse", reponse));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private String genererContexte(List<Ressource> resources, List<MaintenanceLog> maintenances) {
        StringBuilder sb = new StringBuilder();
        sb.append("RESSOURCES :\n");
        for (Ressource r : resources) {
            sb.append("- ").append(r.getNom())
                    .append(" (").append(r.getType())
                    .append(", état: ").append(r.getEtat())
                    .append(", valeur: ").append(r.getValeur())
                    .append("€)\n");
        }

        sb.append("\nMAINTENANCES :\n");
        for (MaintenanceLog m : maintenances) {
            sb.append("- ").append(m.getTypeIntervention())
                    .append(" le ").append(m.getDate())
                    .append(" : ").append(m.getDescription())
                    .append("\n");
        }

        return sb.toString();
    }

    private String appelerGemini(String question, String contexte, String apiKey) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

            RestTemplate restTemplate = new RestTemplate();

            Map<String, Object> body = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, String> parts = new HashMap<>();
            parts.put("text", "Contexte:\n" + contexte + "\n\nQuestion: " + question);
            content.put("parts", new Object[]{parts});
            body.put("contents", new Object[]{content});

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            // Extraire la réponse
            List<Map> candidates = (List<Map>) response.getBody().get("candidates");
            Map firstCandidate = candidates.get(0);
            Map responseContent = (Map) firstCandidate.get("content");
            List<Map> responseParts = (List<Map>) responseContent.get("parts");

            return (String) responseParts.get(0).get("text");

        } catch (Exception e) {
            return "Désolé, erreur : " + e.getMessage();
        }
    }


}