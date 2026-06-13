package tn.cityvoice.actualiteservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket natif (sans SockJS) — Angular se connecte via ws://localhost:8083/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Préfixe pour les destinations gérées par les @MessageMapping controllers
        registry.setApplicationDestinationPrefixes("/app");
        // Broker simple en mémoire — les clients s'abonnent à /topic/...
        registry.enableSimpleBroker("/topic");
    }
}
