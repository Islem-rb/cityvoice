package tn.cityvoice.userservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tn.cityvoice.userservice.entity.User;
import tn.cityvoice.userservice.entity.enums.Role;
import tn.cityvoice.userservice.repository.UserRepository;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.oauth2.redirect-uri}")
    String redirectUri;

    // ← @Lazy sur PasswordEncoder pour casser le cycle
    public OAuth2SuccessHandler(
            UserRepository userRepository,
            @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String nom   = oAuth2User.getAttribute("name");

        // ── Photo — Facebook retourne un Map, Google retourne une String ──
        final String photo = extractPhoto(oAuth2User);

        // ── Email fallback pour Facebook (email non autorisé) ─────────────
        if (email == null) {
            String fbId = oAuth2User.getAttribute("id");
            email = (fbId != null ? fbId : UUID.randomUUID().toString()) + "@facebook.com";
        }

        if (nom == null) nom = "Utilisateur";

        final String finalEmail = email;
        final String finalNom   = nom;

        try {
            User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
                User newUser = new User();
                newUser.setNom(finalNom);
                newUser.setEmail(finalEmail);
                newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                newUser.setRole(Role.CITOYEN);
                newUser.setPhoto(photo);
                return userRepository.save(newUser);
            });

            String token = JwtUtil.generateToken(finalEmail);

            String redirectUrl = redirectUri
                    + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "&userId=" + URLEncoder.encode(user.getId().toString(), StandardCharsets.UTF_8)
                    + "&role=" + URLEncoder.encode(user.getRole().name(), StandardCharsets.UTF_8)
                    + "&email=" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);

            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect(redirectUri + "?error=server_error");
        }
    }

    // ── Helper : extrait l'URL de la photo peu importe le provider ────────
    @SuppressWarnings("unchecked")
    private String extractPhoto(OAuth2User oAuth2User) {
        Object pictureAttr = oAuth2User.getAttributes().get("picture");

        if (pictureAttr == null) return "";

        // Google → String directement
        if (pictureAttr instanceof String url) {
            // ← Agrandir la photo Google (s96 → s400)
            return url.replace("=s96-c", "=s400-c")
                    .replace("=s50", "=s400");
        }

        // Facebook → { data: { url: "...", ... } }
        if (pictureAttr instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> pictureMap =
                    (java.util.Map<String, Object>) pictureAttr;
            Object data = pictureMap.get("data");
            if (data instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dataMap =
                        (java.util.Map<String, Object>) data;
                Object url = dataMap.get("url");
                return url != null ? url.toString() : "";
            }
        }

        return "";
    }
}