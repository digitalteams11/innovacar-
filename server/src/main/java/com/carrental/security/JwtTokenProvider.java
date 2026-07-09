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
import java.util.UUID;

/**
 * Stateless JWT utility — generates and validates access and refresh tokens.
 *
 * <p>Access token payload claims:
 * <ul>
 *   <li>{@code sub}       – user e-mail</li>
 *   <li>{@code tenantId}  – owning tenant identifier</li>
 *   <li>{@code role}      – user role</li>
 *   <li>{@code type}      – token type (access/refresh)</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final Key signingKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String base64Secret,
            @Value("${app.jwt.expiration-ms}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured with a Base64-encoded 256-bit secret.");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes for HS256.");
        }
        this.signingKey  = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    // ── Access Token Generation ─────────────────────────────────────────────

    public String generateToken(User user) {
        return generateToken(user, null);
    }

    public String generateToken(User user, Long sessionId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMs);

        JwtBuilder builder = Jwts.builder()
                .setSubject(user.getEmail())
                .claim("role",     user.getRole().name())
                .claim("type",     "access")
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setId(UUID.randomUUID().toString());
        if (user.getTenant() != null) builder.claim("tenantId", user.getTenant().getId());
        if (sessionId != null) builder.claim("sessionId", sessionId);
        return builder
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ── Refresh Token Generation ────────────────────────────────────────────

    public String generateRefreshToken(User user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        JwtBuilder builder = Jwts.builder()
                .setSubject(user.getEmail())
                .claim("role",     user.getRole().name())
                .claim("type",     "refresh")
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setId(UUID.randomUUID().toString());
        if (user.getTenant() != null) builder.claim("tenantId", user.getTenant().getId());
        return builder
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

    public Long getSessionId(String token) {
        try {
            return parseClaims(token).get("sessionId", Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Challenge Token (short-lived, 2FA step only) ────────────────────────

    private static final long CHALLENGE_EXPIRATION_MS = 5 * 60 * 1000L; // 5 minutes

    public String generateChallengeToken(User user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + CHALLENGE_EXPIRATION_MS);
        JwtBuilder builder = Jwts.builder()
                .setSubject(user.getEmail())
                .claim("type",   "2fa_challenge")
                .claim("userId", user.getId())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setId(UUID.randomUUID().toString());
        if (user.getTenant() != null) builder.claim("tenantId", user.getTenant().getId());
        return builder.signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    public boolean isChallengeToken(String token) {
        try {
            return "2fa_challenge".equals(parseClaims(token).get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    // ── Type checks ─────────────────────────────────────────────────────────

    public boolean isAccessToken(String token) {
        return "access".equals(parseClaims(token).get("type", String.class));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseClaims(token).get("type", String.class));
    }

    public Date getExpiration(String token) {
        return parseClaims(token).getExpiration();
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

    public boolean validateAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "access".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}
