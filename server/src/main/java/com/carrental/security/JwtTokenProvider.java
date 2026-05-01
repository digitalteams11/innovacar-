package com.carrental.security;

import com.carrental.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

/**
 * Stateless JWT utility — generates and validates tokens.
 *
 * <p>Payload claims:
 * <ul>
 *   <li>{@code sub}       – user e-mail</li>
 *   <li>{@code tenantId}  – owning tenant identifier</li>
 *   <li>{@code role}      – user role (ADMIN / AGENT / CLIENT)</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final Key signingKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String base64Secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        this.signingKey  = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    // ── Generation ──────────────────────────────────────────────────────────

    public String generateToken(User user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("tenantId", user.getTenant().getId())
                .claim("role",     user.getRole().name())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ── Parsing helpers ─────────────────────────────────────────────────────

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getTenantId(String token) {
        return parseClaims(token).get("tenantId", Long.class);
    }

    // ── Validation ──────────────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }
}
