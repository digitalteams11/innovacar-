package com.carrental.security.oauth2;

import com.carrental.dto.AuthResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the single-use exchange-code handoff at the center of the
 * post-Google-login "session expired" production bug: a code must be
 * consumable exactly once, an unknown code must fail closed (not throw),
 * and an expired-but-still-present entry must be treated as gone.
 */
class OAuth2ExchangeCodeStoreTest {

    private final OAuth2ExchangeCodeStore store = new OAuth2ExchangeCodeStore();

    private AuthResponse sampleAuthResponse() {
        return AuthResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .userId(1L)
                .tenantId(10L)
                .email("user@example.com")
                .build();
    }

    @Test
    void storeThenConsume_returnsTheSameAuthResponse() {
        AuthResponse issued = sampleAuthResponse();
        String code = store.store(issued);

        AuthResponse consumed = store.consume(code);

        assertThat(consumed).isNotNull();
        assertThat(consumed.getUserId()).isEqualTo(1L);
        assertThat(consumed.getTenantId()).isEqualTo(10L);
    }

    @Test
    void consume_isSingleUse() {
        String code = store.store(sampleAuthResponse());

        assertThat(store.consume(code)).isNotNull();
        assertThat(store.consume(code)).isNull();
    }

    @Test
    void consume_unknownCode_returnsNullWithoutThrowing() {
        assertThat(store.consume("never-issued")).isNull();
    }

    @Test
    void consume_nullOrBlankCode_returnsNull() {
        assertThat(store.consume(null)).isNull();
        assertThat(store.consume("")).isNull();
        assertThat(store.consume("   ")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void consume_expiredEntry_returnsNullEvenThoughStillPresent() throws Exception {
        String code = store.store(sampleAuthResponse());

        // Backdate the entry's expiry via reflection rather than sleeping in a
        // test — TTL_SECONDS is a fixed constant, not an injectable clock.
        Field storeField = OAuth2ExchangeCodeStore.class.getDeclaredField("store");
        storeField.setAccessible(true);
        Map<String, Object> raw = (Map<String, Object>) storeField.get(store);
        Object expiredEntry = newExpiredEntry(raw.get(code));
        raw.put(code, expiredEntry);

        assertThat(store.consume(code)).isNull();
    }

    private Object newExpiredEntry(Object currentEntry) throws Exception {
        Class<?> entryClass = Class.forName(
                OAuth2ExchangeCodeStore.class.getName() + "$Entry");
        Field authResponseField = entryClass.getDeclaredField("authResponse");
        authResponseField.setAccessible(true);
        Object authResponse = authResponseField.get(currentEntry);
        var constructor = entryClass.getDeclaredConstructor(AuthResponse.class, Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(authResponse, Instant.now().minusSeconds(1));
    }
}
