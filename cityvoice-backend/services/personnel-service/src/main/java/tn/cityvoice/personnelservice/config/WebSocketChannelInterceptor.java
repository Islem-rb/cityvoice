package tn.cityvoice.personnelservice.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Intercepte la frame STOMP CONNECT et relit le header "login"
 * (envoyé par le client Angular dans connectHeaders) pour créer un
 * Principal stable, utilisé par convertAndSendToUser().
 *
 * Sans ce composant, le HandshakeHandler assigne un UUID aléatoire
 * sur la connexion SockJS /info et le routage échoue.
 */
@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String userId = accessor.getFirstNativeHeader("login");

            if (userId != null && !userId.isBlank()) {
                accessor.setLeaveMutable(true);   // ← Obligatoire pour remplacer le Principal
                accessor.setUser(() -> userId);
            }
        }

        return message;
    }
}