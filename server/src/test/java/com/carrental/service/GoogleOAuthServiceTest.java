package com.carrental.service;

import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.exception.GoogleAuthException;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for GoogleOAuthService's security gaps: the audience
 * check previously only enforced when app.google.client-id happened to be
 * non-blank (so an unconfigured server silently accepted an ID token issued
 * to ANY Google OAuth client), email_verified was read but never enforced,
 * and blocked/suspended/locked accounts were never checked at all — Google
 * OAuth bypasses Spring Security's AuthenticationManager entirely, so none
 * of the checks that normally protect password login applied to it.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class GoogleOAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SessionService sessionService;
    @Mock private EmailService emailService;
    @Mock private TwoFactorService twoFactorService;

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private GoogleOAuthService service;

    @BeforeEach
    void setUp() {
        service = new GoogleOAuthService(userRepository, tenantRepository, jwtTokenProvider,
                refreshTokenService, sessionService, emailService, twoFactorService);
        ReflectionTestUtils.setField(service, "googleClientId", "real-client-id.apps.googleusercontent.com");
        ReflectionTestUtils.setField(service, "webClient", webClient);
    }

    private void stubGoogleResponse(Map<String, Object> body) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), (Object) any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(body));
    }

    private void stubGoogleCallFails() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), (Object) any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(new RuntimeException("token expired")));
    }

    private Map<String, Object> googleResponse(String aud, boolean emailVerified) {
        return Map.of(
                "aud", aud,
                "sub", "google-sub-123",
                "email", "USER@Example.com",
                "name", "Test User",
                "given_name", "Test",
                "email_verified", emailVerified
        );
    }

    private User existingUser(Tenant tenant) {
        return User.builder()
                .id(1L)
                .email("user@example.com")
                .role(com.carrental.entity.Role.ADMIN)
                .tenant(tenant)
                .emailVerified(false)
                .accountEnabled(true)
                .build();
    }

    private Tenant activeTenant() {
        return Tenant.builder().id(10L).name("Acme Rentals").status("ACTIVE").build();
    }

    @Test
    void unconfiguredClientId_rejectsWithoutCallingGoogle() {
        ReflectionTestUtils.setField(service, "googleClientId", "");

        assertThatThrownBy(() -> service.authenticate("any-token"))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("GOOGLE_AUTH_NOT_CONFIGURED"));
        verifyNoInteractions(webClient, userRepository);
    }

    @Test
    void wrongAudience_isRejected() {
        stubGoogleResponse(googleResponse("some-other-app.apps.googleusercontent.com", true));

        assertThatThrownBy(() -> service.authenticate("token"))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("GOOGLE_TOKEN_INVALID"));
    }

    @Test
    void expiredOrInvalidToken_isRejected() {
        stubGoogleCallFails();

        assertThatThrownBy(() -> service.authenticate("expired-token"))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("GOOGLE_TOKEN_INVALID"));
    }

    @Test
    void unverifiedEmail_isRejected() {
        stubGoogleResponse(googleResponse("real-client-id.apps.googleusercontent.com", false));

        assertThatThrownBy(() -> service.authenticate("token"))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("GOOGLE_EMAIL_NOT_VERIFIED"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void existingUserFoundByEmail_isLinkedNotDuplicated() {
        stubGoogleResponse(googleResponse("real-client-id.apps.googleusercontent.com", true));
        Tenant tenant = activeTenant();
        User user = existingUser(tenant);
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("refresh-token");
        when(sessionService.createSession(eq(1L), any(), any(), any(), anyLong()))
                .thenReturn(com.carrental.entity.UserSession.builder().id(99L).build());
        when(jwtTokenProvider.generateToken(user, 99L)).thenReturn("access-token");
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(86400000L);

        service.authenticate("token");

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(argThat(u -> !u.getEmail().equals("user@example.com")));
        assertThat(user.getGoogleId()).isEqualTo("google-sub-123");
        assertThat(user.getEmailVerified()).isTrue();
    }

    @Test
    void blockedUser_cannotLogInThroughGoogle() {
        stubGoogleResponse(googleResponse("real-client-id.apps.googleusercontent.com", true));
        Tenant tenant = activeTenant();
        User user = existingUser(tenant);
        user.setAccountEnabled(false);
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate("token"))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("ACCOUNT_BLOCKED"));
    }

    @Test
    void suspendedTenant_cannotLogInThroughGoogle() {
        stubGoogleResponse(googleResponse("real-client-id.apps.googleusercontent.com", true));
        Tenant tenant = Tenant.builder().id(10L).name("Acme Rentals").status("SUSPENDED").build();
        User user = existingUser(tenant);
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate("token"))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("ACCOUNT_SUSPENDED"));
    }

    @Test
    void validNewUser_createsAgencyAndReturnsAdminRole() {
        stubGoogleResponse(googleResponse("real-client-id.apps.googleusercontent.com", true));
        when(userRepository.findByGoogleId("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        Tenant savedTenant = Tenant.builder().id(20L).name("Test User's Agency").build();
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);
        User savedUser = User.builder().id(2L).email("user@example.com").role(com.carrental.entity.Role.ADMIN)
                .tenant(savedTenant).emailVerified(true).accountEnabled(true).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateRefreshToken(savedUser)).thenReturn("refresh-token");
        when(sessionService.createSession(eq(2L), any(), any(), any(), anyLong()))
                .thenReturn(com.carrental.entity.UserSession.builder().id(77L).build());
        when(jwtTokenProvider.generateToken(savedUser, 77L)).thenReturn("access-token");
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(86400000L);

        var response = service.authenticate("token");

        assertThat(response.getRole()).isEqualTo(com.carrental.entity.Role.ADMIN);
        assertThat(response.getTenantId()).isEqualTo(20L);
        verify(tenantRepository).save(any(Tenant.class));
        verify(emailService).sendWelcomeEmail(eq("user@example.com"), any());
    }
}
