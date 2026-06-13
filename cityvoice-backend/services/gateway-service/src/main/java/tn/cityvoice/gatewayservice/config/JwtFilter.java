package tn.cityvoice.gatewayservice.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.List;

@Component
public class JwtFilter implements GlobalFilter, Ordered {

    private static final String SECRET = "cityvoice-secret-key-cityvoice-secret-key";
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    private static final List<String> OPEN_PATHS = List.of(
            "/api/auth/",
            "/oauth2/",
            "/login/oauth2/",

            "/api/locations/municipalities",
            "/api/ai/screen-name",

            // public signalement data used in landing/chatbot
            "/api/v1/signalements/stats",
            "/api/v1/signalements",

            // audio public
            "/api/v1/hybrid-voice/audio/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // Very important for CORS preflight
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        boolean isOpen = OPEN_PATHS.stream().anyMatch(path::startsWith);

        if (isOpen) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = authHeader.substring(7);

            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}