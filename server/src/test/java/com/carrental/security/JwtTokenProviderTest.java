package com.carrental.security;

import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenProvider}.
 * No Spring context needed — pure unit tests.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    /** 256-bit Base64 key used only for tests. Runtime secrets come from JWT_SECRET. */
    private static final String SECRET =
        "Y2FycmVudGFsLXN1cGVyLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLTI1NmJpdHM=";
    private static final long EXP_MS = 86_400_000L;
    private static final long REFRESH_EXP_MS = 604_800_000L;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, EXP_MS, REFRESH_EXP_MS);
    }

    private User buildUser() {
        Tenant tenant = Tenant.builder()
                .id(1L)
                .name("Acme Rentals")
                .email("billing@acme.com")
                .subscriptionActive(true)
                .subscriptionEndDate(LocalDate.now().plusYears(1))
                .build();

        return User.builder()
                .id(42L)
                .email("admin@acme.com")
                .password("hashed")
                .role(Role.ADMIN)
                .tenant(tenant)
                .build();
    }

    @Test
    void generateAndValidateToken() {
        User  user  = buildUser();
        String token = provider.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void claimsContainCorrectEmailAndTenantId() {
        User   user     = buildUser();
        String token    = provider.generateToken(user);

        assertThat(provider.getEmail(token)).isEqualTo("admin@acme.com");
        assertThat(provider.getTenantId(token)).isEqualTo(1L);
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertThat(provider.validateToken("not.a.jwt.token")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void missingSecretFailsFastWithClearMessage() {
        assertThatThrownBy(() -> new JwtTokenProvider("", EXP_MS, REFRESH_EXP_MS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be configured");
    }

    @Test
    void weakSecretFailsFastWithClearMessage() {
        String weakSecret = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new JwtTokenProvider(weakSecret, EXP_MS, REFRESH_EXP_MS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
