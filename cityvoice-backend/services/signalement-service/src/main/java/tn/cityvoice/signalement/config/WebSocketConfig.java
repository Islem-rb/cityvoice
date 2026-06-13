package tn.cityvoice.signalement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration WebSocket STOMP pour les notifications temps réel.
 *
 * Flux :
 *   Angular se connecte sur  → ws://localhost:8082/ws
 *   S'abonne au topic        → /topic/notifications/{userId}
 *   Le backend pousse sur    → /topic/notifications/{userId}
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broker simple en mémoire pour les topics de diffusion
        config.enableSimpleBroker("/topic");
        // Préfixe pour les messages entrants (côté client → serveur)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket natif (pas de SockJS — compatibilité Angular 18 + Vite)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
