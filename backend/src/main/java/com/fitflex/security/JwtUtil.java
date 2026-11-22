package com.fitflex.security;

import com.fitflex.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates and checks JSON Web Tokens. A JWT has three parts, header.payload.signature;
 * the signature (HMAC-SHA256 with our secret key) proves the server issued the token
 * and that nobody changed the payload (email, role, expiry) afterwards.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${fitflex.jwt.secret:}") String secret,
                   @Value("${fitflex.jwt.expiration-ms:86400000}") long expirationMs) {
        this.expirationMs = expirationMs;
        if (secret == null || secret.isBlank()) {
            // Why: we never hardcode a secret in the code. In development a random key is fine,
            // it only means everyone has to log in again after a restart.
            log.warn("JWT_SECRET is not set: using a random key. Tokens will be invalid after a restart.");
            this.key = Jwts.SIG.HS256.key().build();
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                // HS256 needs a key of at least 256 bits (32 bytes); jjwt refuses shorter keys.
                throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
            }
            this.key = Keys.hmacShaKeyFor(bytes);
        }
    }

    /** Builds a signed token for this user that expires after the configured time (24 h by default). */
    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("uid", user.getId())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the email inside a valid token, or null if the token is invalid, tampered with
     * or expired. jjwt checks the signature and the expiry date for us.
     */
    public String extractEmailIfValid(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getMessage());
            return null;
        }
    }
}
