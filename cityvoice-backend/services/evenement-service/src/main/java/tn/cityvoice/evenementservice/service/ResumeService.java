package tn.cityvoice.evenementservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.cityvoice.evenementservice.entity.Evenement;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private final RestTemplate restTemplate;

    // ── Appel Ollama Llama3.2 ──────────────────────
    private String appellerOllama(String prompt, int maxTokens) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model",   "llama3.2",
                    "prompt",  prompt,
                    "stream",  false,
                    "options", Map.of(
                            "num_predict", maxTokens,
                            "temperature", 0.8,
                            "top_p",       0.9
                    )
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://localhost:11434/api/generate",
                    request, Map.class
            );

            if (response.getBody() != null) {
                String result = (String) response.getBody().get("response");
                return result != null ? result.trim() : "";
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }

    // ── Texte partage social media ─────────────────
    public String genererTextePartage(Evenement ev, String plateforme) {
        String prix = ev.getEstPayant()
                ? ev.getPrix() + " TND" : "Gratuit";
        String desc = ev.getDescription() != null
                ? ev.getDescription().substring(0,
                Math.min(150, ev.getDescription().length()))
                : "";

        String prompt = switch (plateforme) {

            case "facebook" -> String.format("""
                Tu es un expert en marketing événementiel tunisien.
                Génère un post Facebook captivant en français.
                IMPORTANT: Utilise ces emojis: 🎊 🗓️ 📍 💫 🎯
                Entre 150 et 250 caractères. Accrocheur et engageant.
                
                DONNÉES:
                Nom: %s
                Date: %s
                Lieu: %s
                Prix: %s
                Type: %s
                Description: %s
                
                Format:
                🎊 [titre accrocheur]
                🗓️ [date]
                📍 [lieu]
                💫 [description courte]
                🎯 Inscrivez-vous sur CityVoice !
                
                Post Facebook:
                """,
                    ev.getTitre(), ev.getDateDebut(),
                    ev.getLieu(), prix, ev.getType(), desc);

            case "whatsapp" -> String.format("""
                Tu es un expert en marketing événementiel tunisien.
                Génère un message WhatsApp COMPLET et DESCRIPTIF en français.
                
                RÈGLES STRICTES:
                - Minimum 150 caractères
                - PAS D'EMOJIS - texte uniquement
                - Inclure OBLIGATOIREMENT: nom, date complète, lieu, prix
                - Description attrayante de l'événement
                - Terminer par un appel à l'action
                
                DONNÉES:
                Nom: %s
                Date: %s
                Lieu: %s
                Prix: %s
                Type: %s
                Description: %s
                
                Format:
                *[titre accrocheur en gras]*
                
                Lieu: [lieu]
                Date: [date complète]
                Prix: [prix]
                
                [description attrayante 2-3 phrases]
                
                Inscrivez-vous sur CityVoice !
                
                Message WhatsApp:
                """,
                    ev.getTitre(), ev.getDateDebut(),
                    ev.getLieu(), prix, ev.getType(), desc);

            case "linkedin" -> String.format("""
                Génère un post LinkedIn professionnel en français.
                Max 200 caractères. Un seul emoji au debut.
                Hashtags: #CityVoice #Tunisie #Evenement
                
                Evenement: %s | Date: %s | Lieu: %s | Type: %s
                
                Post LinkedIn:
                """,
                    ev.getTitre(), ev.getDateDebut(),
                    ev.getLieu(), ev.getType());

            case "twitter" -> String.format("""
                Génère un tweet percutant en français. MAX 200 caractères.
                IMPORTANT: Utilise ces emojis: 🔥 📍 📅
                Hashtags: #CityVoice #Tunisie #Evenement
                
                DONNÉES:
                Nom: %s | Date: %s | Lieu: %s | Prix: %s
                
                Tweet:
                """,
                    ev.getTitre(), ev.getDateDebut(),
                    ev.getLieu(), prix);

            default -> ev.getTitre();
        };

        return appellerOllama(prompt, 250);
    }

    // ── Résumé court ───────────────────────────────
    public String genererResumeCourt(Evenement ev) {
        String prompt = String.format("""
            Tu es un rédacteur événementiel tunisien.
            Génère un résumé court et captivant en français (max 80 mots).
            Inclure : titre, date, lieu, type, points clés.
            Ton : enthousiaste et professionnel.
            
            Titre: %s | Lieu: %s | Date: %s | Type: %s
            Description: %s
            
            Résumé captivant:
            """,
                ev.getTitre(), ev.getLieu(), ev.getDateDebut(),
                ev.getType(),
                ev.getDescription() != null ? ev.getDescription() : "");
        return appellerOllama(prompt, 200);
    }

    // ── Notification push mobile ───────────────────
    public String genererNotification(Evenement ev) {
        String prompt = String.format("""
            Génère une notification push mobile courte en français.
            MAX 80 caractères. Urgent et accrocheur.
            Utilise 1 emoji au début.
            
            Événement: %s | Date: %s | Lieu: %s
            
            Notification:
            """,
                ev.getTitre(), ev.getDateDebut(), ev.getLieu());
        return appellerOllama(prompt, 100);
    }

    // ── Email d'invitation ─────────────────────────
    public String genererEmailInvitation(Evenement ev) {
        String prix = ev.getEstPayant()
                ? ev.getPrix() + " TND" : "Gratuit";
        String prompt = String.format("""
            Tu es un expert en communication événementielle tunisienne.
            Génère un email d'invitation complet et professionnel en français.
            Inclure : objet accrocheur, corps descriptif, appel à l'action.
            Ton : chaleureux et professionnel. Max 150 mots.
            
            Événement: %s | Date: %s | Lieu: %s
            Prix: %s | Type: %s
            Description: %s
            
            Email complet:
            """,
                ev.getTitre(), ev.getDateDebut(), ev.getLieu(),
                prix, ev.getType(),
                ev.getDescription() != null ? ev.getDescription() : "");
        return appellerOllama(prompt, 350);
    }
}