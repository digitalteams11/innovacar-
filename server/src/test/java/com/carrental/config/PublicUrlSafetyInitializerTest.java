package com.carrental.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the production incident where the "Download Contract
 * PDF" email button opened http://localhost:8082 because PUBLIC_API_URL was
 * never set on Railway and app.public-api-url silently fell back to its local
 * dev default. This initializer must fail startup outright in that situation
 * instead of letting a broken localhost link reach a real inbox.
 */
class PublicUrlSafetyInitializerTest {

    private final PublicUrlSafetyInitializer initializer = new PublicUrlSafetyInitializer();

    private ConfigurableApplicationContext contextWith(String[] activeProfiles, String frontendUrl, String publicApiUrl) {
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        when(environment.getActiveProfiles()).thenReturn(activeProfiles);
        when(environment.getProperty("app.frontend-url", "")).thenReturn(frontendUrl);
        when(environment.getProperty("app.public-api-url", "")).thenReturn(publicApiUrl);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }

    @Test
    void localDev_neverEnforced_evenWithLocalhostUrls() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"dev"}, "http://localhost:5173", "http://localhost:8082");

        assertThatNoException().isThrownBy(() -> initializer.initialize(context));
    }

    @Test
    void testProfile_neverEnforced() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"test"}, "http://localhost:5173", "http://localhost:8082");

        assertThatNoException().isThrownBy(() -> initializer.initialize(context));
    }

    @Test
    void prodProfile_validHttpsUrls_startsCleanly() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://innovacar.app", "https://api.innovacar.app");

        assertThatNoException().isThrownBy(() -> initializer.initialize(context));
    }

    @Test
    void prodProfile_missingPublicApiUrl_failsFast() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://innovacar.app", "");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC_URL_MISSING")
                .hasMessageContaining("app.public-api-url");
    }

    @Test
    void prodProfile_localhostPublicApiUrl_failsFast() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://innovacar.app", "http://localhost:8082");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSING_LOCALHOST_PUBLIC_URL");
    }

    @Test
    void prodProfile_httpInsteadOfHttps_failsFast() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "http://innovacar.app", "https://api.innovacar.app");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSING_INSECURE_PUBLIC_URL");
    }

    @Test
    void prodProfile_railwayInternalHost_failsFast() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://innovacar.app", "https://backend.railway.internal");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSING_LOCALHOST_PUBLIC_URL");
    }

    @Test
    void prodProfile_privateIpHost_failsFast() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://innovacar.app", "https://192.168.1.5");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSING_LOCALHOST_PUBLIC_URL");
    }

    @Test
    void prodProfile_privateClassAIpHost_failsFast() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://innovacar.app", "http://10.0.0.1");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSING_LOCALHOST_PUBLIC_URL");
    }

    @Test
    void prodProfile_dockerInternalHost_failsFast() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://innovacar.app", "https://host.docker.internal");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REFUSING_LOCALHOST_PUBLIC_URL");
    }

    @Test
    void prodProfile_wwwFrontendHost_isAccepted() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://www.innovacar.app", "https://api.innovacar.app");

        assertThatNoException().isThrownBy(() -> initializer.initialize(context));
    }

    @Test
    void prodProfile_unexpectedHost_failsFast() {
        ConfigurableApplicationContext context = contextWith(
                new String[]{"prod"}, "https://innovacar.app", "https://some-other-domain.example");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNEXPECTED_PUBLIC_URL_HOST");
    }

}
