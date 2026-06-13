package tn.cityvoice.actualiteservice.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.security.Key;

public class JwtUtil {

    private static final String SECRET = "cityvoice-secret-key-cityvoice-secret-key";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    public static String getEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}