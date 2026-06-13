package tn.cityvoice.personnelservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class GroqCvService {

    private static final String API_KEY = System.getenv("GROQ_API_KEY");

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.1-8b-instant";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GroqCvService() {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    public byte[] genererCvPdf(String nomUtilisateur, String descriptionCandidature, String fonction) {
        String cvContenu = appelerGroq(nomUtilisateur, descriptionCandidature, fonction);
        return convertirEnPdf(nomUtilisateur, cvContenu, fonction);
    }

    private String appelerGroq(String nomUtilisateur, String descriptionCandidature, String fonction) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Clé API Groq manquante (property groq.api.key)");
        }
        String prompt = String.format("""
            Tu es un expert en rédaction de CV.
            Génère un CV professionnel pour %s.
            
            POSTE VISÉ : %s
            
            DESCRIPTION DE L'OFFRE :
            %s
            
            Structure la réponse ainsi :
            
            ===== CV GÉNÉRÉ =====
            
            📌 RÉSUMÉ PROFESSIONNEL
            [Résumé de 3-4 lignes adapté au poste]
            
            💼 EXPÉRIENCES
            • [Expérience suggérée 1]
            • [Expérience suggérée 2]
            
            🛠️ COMPÉTENCES CLÉS
            • [Compétence 1]
            • [Compétence 2]
            
            🎓 FORMATION
            • [Formation suggérée]
            
            🌐 LANGUES
            • [Langue 1]
            • [Langue 2]
            """, nomUtilisateur, fonction != null ? fonction : "Non spécifié", descriptionCandidature);

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", "Tu es un expert en CV professionnels. Réponds uniquement en français."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7,
                "max_tokens", 1500
        );

        try {
            String response = webClient.post()
                    .uri(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            throw new RuntimeException("Erreur API Groq: " + e.getMessage(), e);
        }
    }

    private byte[] convertirEnPdf(String nomUtilisateur, String contenuCv, String fonction) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font subtitleFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

            // Titre
            Paragraph title = new Paragraph("CURRICULUM VITAE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            // Nom
            Paragraph name = new Paragraph(nomUtilisateur.toUpperCase(), subtitleFont);
            name.setAlignment(Element.ALIGN_CENTER);
            document.add(name);
            document.add(new Paragraph(" "));

            // Date
            Paragraph date = new Paragraph("Généré le " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), normalFont);
            date.setAlignment(Element.ALIGN_RIGHT);
            document.add(date);
            document.add(new Paragraph(" "));

            document.add(new Paragraph("__________________________________________________________", normalFont));
            document.add(new Paragraph(" "));

            // Contenu
            String[] lignes = contenuCv.split("\n");
            for (String ligne : lignes) {
                if (ligne.trim().isEmpty()) {
                    document.add(new Paragraph(" "));
                } else if (ligne.contains("📌") || ligne.contains("💼") || ligne.contains("🛠️") ||
                        ligne.contains("🎓") || ligne.contains("🌐") || ligne.contains("=====")) {
                    Paragraph p = new Paragraph(ligne, subtitleFont);
                    document.add(p);
                } else if (ligne.trim().startsWith("•")) {
                    Paragraph p = new Paragraph(ligne, normalFont);
                    p.setIndentationLeft(15);
                    document.add(p);
                } else {
                    Paragraph p = new Paragraph(ligne, normalFont);
                    document.add(p);
                }
            }

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF: " + e.getMessage(), e);
        }
    }
}