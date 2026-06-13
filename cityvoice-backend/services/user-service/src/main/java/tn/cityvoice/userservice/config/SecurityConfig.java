package tn.cityvoice.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(
            @Lazy OAuth2SuccessHandler oAuth2SuccessHandler,
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ❌ REMOVED: .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/oauth2/**").permitAll()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestResolver(
                                        customAuthorizationRequestResolver(clientRegistrationRepository)
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(
            ClientRegistrationRepository repo
    ) {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver =
                new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                OAuth2AuthorizationRequest req = defaultResolver.resolve(request);
                return customize(req);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                OAuth2AuthorizationRequest req = defaultResolver.resolve(request, clientRegistrationId);
                return customize(req);
            }

            private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req) {
                if (req == null) {
                    return null;
                }

                String registrationId = (String) req.getAttributes().get("registration_id");
                Map<String, Object> params = new HashMap<>(req.getAdditionalParameters());

                if ("google".equals(registrationId)) {
                    params.put("prompt", "select_account");
                    params.put("access_type", "offline");
                }

                return OAuth2AuthorizationRequest.from(req)
                        .additionalParameters(params)
                        .build();
            }
        };
    }

    // ❌ REMOVED: corsConfigurationSource() bean - Gateway handles CORS now

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}