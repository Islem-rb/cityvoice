package tn.cityvoice.personnelservice.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * CORRECTION : SockJS appelle d'abord /ws-notifications/info (sans query param),
 * puis /ws-notifications/{server}/{session}/websocket — l'URI ne contient pas
 * toujours le userId.  On lit le param s'il est là, sinon on génère un UUID
 * temporaire ; le vrai routage est assuré par WebSocketChannelInterceptor
 * qui lit le header "login" de la frame STOMP CONNECT.
 */
public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String query  = request.getURI().getQuery();
        String userId = null;

        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("userId=")) {
                    userId = param.substring("userId=".length()).trim();
                    break;
                }
            }
        }

        // Fallback — sera écrasé par le ChannelInterceptor au CONNECT STOMP
        final String name = (userId != null && !userId.isBlank())
                ? userId
                : UUID.randomUUID().toString();

        return () -> name;
    }
}