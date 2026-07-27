package com.carrental.security.oauth2;

import com.carrental.dto.AuthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-use, short-TTL handoff for the real JWT pair produced by a
 * successful Google OAuth2 login.
 *
 * <p>Why this exists instead of putting tokens directly in the redirect URL
 * or in a cookie: this is a browser top-level redirect back from Google, and
 * the SPA's existing token transport is a JSON response body (see
 * AuthController's {@code authResponse()} / axios's Bearer-header +
 * localStorage handling) — not cookies (mixing the two would also break the
 * JwtAuthenticationFilter's cookie-transport CSRF guard for every later
 * request). Putting real access/refresh tokens in a URL is itself an
 * anti-pattern (they'd land in browser history, the Referer header, and
 * server access logs). Instead, {@link OAuth2LoginSuccessHandler} redirects
 * with a single opaque, random, single-use code; the frontend immediately
 * exchanges it via {@code POST /api/auth/oauth2/exchange} (see
 * {@code AuthController}) for the real {@link AuthResponse} over a normal
 * JSON call — identical shape/mechanism to password login.
 *
 * <p>In-memory and per-instance: acceptable because this server currently
 * runs as a single Railway instance and the code is consumed within seconds
 * of being issued (60s TTL) — if the deployment ever scales to multiple
 * instances behind a shared load balancer without session affinity, this
 * would need to move to a shared store (e.g. Redis) instead.
 */
@Slf4j
@Component
public class OAuth2ExchangeCodeStore {

    // Was 60s; widened to 180s (matching CookieOAuth2AuthorizationRequestRepository's
    // own window) as defensive slack against the extra hop a www->non-www redirect,
    // a slow mobile network, or a cold Vercel/CDN load adds between this code being
    // issued and the frontend's exchange call actually reaching this endpoint.
    private static final long TTL_SECONDS = 180;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** Issues a new single-use code for this auth response. */
    public String store(AuthResponse authResponse) {
        String code = UUID.randomUUID().toString() + UUID.randomUUID();
        store.put(code, new Entry(authResponse, Instant.now().plusSeconds(TTL_SECONDS)));
        return code;
    }

    /**
     * Consumes (removes) the code and returns its response, or {@code null}
     * if the code is unknown, already used, or expired. Removal happens
     * unconditionally on lookup so a code can never be replayed even if the
     * caller ignores the null/expired result.
     */
    public AuthResponse consume(String code) {
        if (code == null || code.isBlank()) return null;
        Entry entry = store.remove(code);
        if (entry == null) {
            // Never issued (bad/garbled param), already consumed once (double
            // call), or this instance restarted/rolled over to a different
            // container since the code was stored — this in-memory, per-instance
            // store (see class Javadoc) can't tell these apart.
            log.warn("[OAUTH2_EXCHANGE_CODE_NOT_FOUND] storeSize={}", store.size());
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            log.warn("[OAUTH2_EXCHANGE_CODE_EXPIRED] ttlSeconds={}", TTL_SECONDS);
            return null;
        }
        return entry.authResponse();
    }

    /** Purges any codes nobody ever exchanged, so abandoned logins don't leak memory. */
    @Scheduled(fixedRate = 60_000)
    void cleanupExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private record Entry(AuthResponse authResponse, Instant expiresAt) {}
}
