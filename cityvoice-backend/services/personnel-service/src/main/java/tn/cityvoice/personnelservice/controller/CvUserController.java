package tn.cityvoice.personnelservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.cityvoice.personnelservice.entity.CandidatureEquipe;
import tn.cityvoice.personnelservice.entity.CvUser;
import tn.cityvoice.personnelservice.entity.EmailRequest;
import tn.cityvoice.personnelservice.entity.PreselectionRequest;
import tn.cityvoice.personnelservice.feign.UserFeignClient;
import tn.cityvoice.personnelservice.repository.CandidatureEquipeRepository;
import tn.cityvoice.personnelservice.repository.MembreRepository;
import tn.cityvoice.personnelservice.service.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/personnel/cvuser")
@RequiredArgsConstructor
@Slf4j
public class CvUserController {

    private final ICvUserImp        service;
    private final UserFeignClient   userFeignClient;
    private final EmailService      emailService;
    private final ExtractionService extractionService;
    private final IAService         iaService;
    private final IAEmbedding       embeddingService;
    private final NotificationService notificationService;
    private final GroqCvService groqCvService;
    private final CandidatureEquipeRepository candidatureEquipeRepository;
    private final MembreRepository membreRepository;

    @PostMapping("/{candidatureId}")
    public CvUser add(@PathVariable("candidatureId") UUID candidatureId,
                      @RequestParam("userId") UUID userId,
                      @RequestParam("file") MultipartFile file) throws Exception {
        return service.addCV(candidatureId, userId, file);
    }

    @GetMapping("/{candidatureId}/hasApplied/{userId}")
    public boolean hasApplied(
            @PathVariable("candidatureId") UUID candidatureId,
            @PathVariable("userId") UUID userId) {
        return service.hasUserApplied(candidatureId, userId);
    }

    @PutMapping("/{cvId}")
    public CvUser update(@PathVariable("cvId") UUID cvId,
                         @RequestParam("file") MultipartFile file) throws Exception {
        return service.updateCV(cvId, file);
    }

    @DeleteMapping("/{cvId}")
    public void delete(@PathVariable("cvId") UUID cvId) {
        service.deleteCV(cvId);
    }

    @GetMapping("/{cvId}")
    public CvUser get(@PathVariable("cvId") UUID cvId) {
        return service.getCV(cvId);
    }

    @GetMapping("/{cvId}/download")
    public ResponseEntity<byte[]> download(@PathVariable("cvId") UUID cvId) {
        CvUser cv = service.getCV(cvId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + cv.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(cv.getData());
    }

    @GetMapping("/candidature/{candidatureId}/with-users")
    public ResponseEntity<List<Map<String, Object>>> getCvsWithUsers(
            @PathVariable("candidatureId") UUID candidatureId) {

        List<CvUser> cvs = service.getAllCVsByCandidature(candidatureId);

        List<Map<String, Object>> result = cvs.stream().map(cv -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cvId",     cv.getId());
            item.put("fileName", cv.getFileName());
            item.put("userId",   cv.getUserId());
            try {
                Map<String, Object> user = userFeignClient.getUserById(cv.getUserId());
                item.put("userName",  user.get("nom"));
                item.put("userEmail", user.get("email"));
                item.put("userPhoto", user.get("photo"));
            } catch (Exception e) {
                item.put("userName",  "Inconnu");
                item.put("userEmail", "—");
                item.put("userPhoto", null);
            }
            return item;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/send-email")
    public ResponseEntity<?> sendEmail(@RequestBody EmailRequest req) {
        emailService.sendEmail(req.getTo(), req.getSubject(), req.getBody());
        return ResponseEntity.ok(Map.of("message", "Email envoyé avec succès"));
    }

    @PostMapping("/{cvId}/score-smart")
    public ResponseEntity<?> scoreSmart(
            @PathVariable("cvId") UUID cvId,
            @RequestBody Map<String, String> body) {

        String description = body.get("description");
        if (description == null || description.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Description manquante dans le body"));
        }

        CvUser cv     = service.getCV(cvId);
        String cvText = extractionService.extractText(cv.getData());
        if (cvText == null || cvText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "CV vide ou illisible"));
        }

        String cleanDescription = extractionService.cleanText(description);
        String cleanCvText      = extractionService.cleanText(cvText);

        log.info(">>> [{}] CV nettoyé : {} chars | Description : {} chars",
                cvId, cleanCvText.length(), cleanDescription.length());

        if (cleanCvText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "CV vide après nettoyage"));
        }

        double similarity;
        try {
            float[] embDesc = embeddingService.embed(cleanDescription);
            float[] embCv   = embeddingService.embed(cleanCvText);
            similarity = embeddingService.cosine(embDesc, embCv);
        } catch (Exception e) {
            log.error(">>> [{}] ERREUR embedding: {}", cvId, e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Service embedding indisponible: " + e.getMessage()));
        }

        if (similarity < 0.2) {
            return ResponseEntity.ok(Map.of(
                    "score",         2,
                    "similarity",    similarity,
                    "justification", "CV tres eloigne du poste"
            ));
        }

        Map<String, Object> llmResult = iaService.scoreCv(cleanDescription, cleanCvText);
        llmResult.put("similarity", similarity);
        return ResponseEntity.ok(llmResult);
    }

    /**
     * Préselectionner un candidat.
     * Envoie un email + une notification WebSocket persistée avec cvId et fonction,
     * afin que le frontend puisse lancer le quiz directement depuis la notification.
     */
    @PostMapping("/{cvId}/preselect")
    public ResponseEntity<?> preselectCv(
            @PathVariable("cvId") UUID cvId,
            @RequestBody PreselectionRequest req) {

        // Empêcher présélection si l'utilisateur est déjà membre d'une équipe
        if (req.getUserId() != null && membreRepository.existsByUserId(req.getUserId())) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Utilisateur déjà membre d'une équipe"));
        }

        // 1. Infos du candidat via Feign
        String userName  = "Candidat";
        String userEmail = null;
        try {
            Map<String, Object> user = userFeignClient.getUserById(req.getUserId());
            userName  = (String) user.getOrDefault("nom", "Candidat");
            userEmail = (String) user.get("email");

            // Fallback: empêcher présélection si l'email est déjà membre
            if (userEmail != null && !userEmail.isBlank()
                    && membreRepository.existsByEmailIgnoreCase(userEmail)) {
                return ResponseEntity.status(409)
                        .body(Map.of("error", "Utilisateur déjà membre d'une équipe"));
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer l'utilisateur {} via Feign", req.getUserId());
        }

        // 2. Email de préselection
        if (userEmail != null) {
            try {
                emailService.sendEmail(
                        userEmail,
                        "Félicitations ! Vous êtes présélectionné(e) — " + req.getEquipeNom(),
                        "Bonjour " + userName + ",\n\n"
                                + "Votre candidature pour le poste :\n" + req.getPoste() + "\n\n"
                                + "a été retenue par l'équipe « " + req.getEquipeNom() + " ».\n\n"
                                + "Cordialement,\nL'équipe CityVoice"
                );
            } catch (Exception e) {
                log.warn("Erreur envoi email préselection : {}", e.getMessage());
            }
        }

        // 3. Notification temps réel avec cvId + fonction ← MODIFIÉ
        UUID chefId = req.getChefEquipeId() != null
                ? req.getChefEquipeId()
                : UUID.fromString("00000000-0000-0000-0000-000000000000");

        notificationService.sendNotification(
                req.getUserId(),
                chefId,
                "🎉 Félicitations ! Test de recrutement — " + req.getPoste(),
                "Bonne nouvelle ! Votre candidature pour le poste « " + req.getPoste() + " » "
                        + "au sein de l'équipe « " + req.getEquipeNom() + " » a été présélectionnée ✅.\n\n"
                        + "📌 Prochaine étape : vous êtes invité(e) à passer un test technique "
                        + "afin de finaliser votre processus de recrutement.\n\n"
                        + "👉 Cliquez sur « Passer le test » ci-dessous.\n\n"
                        + "Bonne chance 🍀,\nL'équipe CityVoice",
                "PRESELECTION",
                cvId,               // ← NOUVEAU : transmis à la notification
                req.getPoste()      // ← NOUVEAU : fonction pour générer le quiz
        );

        return ResponseEntity.ok(Map.of("message", "Candidat présélectionné avec succès"));
    }
    @PostMapping("/generer-ia/{candidatureId}")
    public ResponseEntity<byte[]> genererCvParIa(
            @PathVariable("candidatureId") UUID candidatureId,
            @RequestParam("userId") UUID userId) {

        // Récupérer la candidature
        CandidatureEquipe candidature = candidatureEquipeRepository.findById(candidatureId)
                .orElseThrow(() -> new RuntimeException("Candidature non trouvée"));

        // Récupérer le nom de l'utilisateur
        String nomUtilisateur = "Candidat";
        try {
            Map<String, Object> user = userFeignClient.getUserById(userId);
            nomUtilisateur = (String) user.getOrDefault("nom", "Candidat");
        } catch (Exception e) {
            log.warn("Impossible de récupérer le nom: {}", e.getMessage());
        }

        // Générer le PDF
        byte[] pdfBytes = groqCvService.genererCvPdf(
                nomUtilisateur,
                candidature.getDescription() != null ? candidature.getDescription() : "",
                candidature.getFonction() != null ? candidature.getFonction() : ""
        );

        // Retourner le PDF
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "CV_" + nomUtilisateur + ".pdf");

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}