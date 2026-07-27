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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for GoogleOAuthService, which resolves an
 * already-verified Google identity (see {@link com.carrental.security.oauth2.CustomOidcUserService})
 * into an Innovacar session: account linking/creation, blocked/suspended/
 * locked account checks (which Google OAuth bypasses Spring Security's
 * AuthenticationManager for, so must be enforced explicitly), and 2FA
 * challenge issuance.
 */
@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SessionService sessionService;
    @Mock private EmailService emailService;

    private GoogleOAuthService service;

    private static final String SUB = "google-sub-123";
    private static final String PICTURE = "https://lh3.googleusercontent.com/a/photo.jpg";

    @BeforeEach
    void setUp() {
        service = new GoogleOAuthService(userRepository, tenantRepository, jwtTokenProvider,
                refreshTokenService, sessionService, emailService);
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

    private void stubAuthResponseCollaborators(User user, Long sessionId) {
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("refresh-token");
        when(sessionService.createSession(eq(user.getId()), any(), any(), any(), anyLong()))
                .thenReturn(com.carrental.entity.UserSession.builder().id(sessionId).build());
        when(jwtTokenProvider.generateToken(user, sessionId)).thenReturn("access-token");
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(86400000L);
    }

    @Test
    void missingSubject_isRejected() {
        assertThatThrownBy(() -> service.authenticateGoogleUser("", "user@example.com", true, "Test User", "Test", PICTURE))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("GOOGLE_TOKEN_INVALID"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void missingEmail_isRejected() {
        assertThatThrownBy(() -> service.authenticateGoogleUser(SUB, "", true, "Test User", "Test", PICTURE))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("GOOGLE_EMAIL_MISSING"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void unverifiedEmail_isRejected() {
        assertThatThrownBy(() -> service.authenticateGoogleUser(SUB, "user@example.com", false, "Test User", "Test", PICTURE))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("GOOGLE_EMAIL_NOT_VERIFIED"));
        verifyNoInteractions(userRepository);
    }

    @Test
    void existingUserFoundByGoogleId_logsInDirectly() {
        Tenant tenant = activeTenant();
        User user = existingUser(tenant);
        user.setGoogleId(SUB);
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        stubAuthResponseCollaborators(user, 99L);

        service.authenticateGoogleUser(SUB, "user@example.com", true, "Test User", "Test", PICTURE);

        verify(userRepository, never()).findByEmail(any());
        verify(tenantRepository, never()).save(any());
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void existingUserFoundByEmail_isLinkedNotDuplicated() {
        Tenant tenant = activeTenant();
        User user = existingUser(tenant);
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        stubAuthResponseCollaborators(user, 99L);

        service.authenticateGoogleUser(SUB, "USER@Example.com", true, "Test User", "Test", PICTURE);

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(argThatEmailIsNot("user@example.com"));
        assertThat(user.getGoogleId()).isEqualTo(SUB);
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getAuthProvider()).isEqualTo(com.carrental.entity.AuthProvider.LOCAL_AND_GOOGLE);
        assertThat(user.getAvatarUrl()).isEqualTo(PICTURE);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void linkingPreservesRoleTenantAndExistingAvatar() {
        Tenant tenant = activeTenant();
        User user = existingUser(tenant);
        user.setRole(com.carrental.entity.Role.EMPLOYEE);
        user.setAvatarUrl("https://innovacar.app/uploads/avatars/1/custom.jpg");
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        stubAuthResponseCollaborators(user, 99L);

        var response = service.authenticateGoogleUser(SUB, "user@example.com", true, "Test User", "Test", PICTURE);

        assertThat(response.getRole()).isEqualTo(com.carrental.entity.Role.EMPLOYEE);
        assertThat(response.getTenantId()).isEqualTo(10L);
        // A user's own uploaded avatar must never be overwritten by Google's photo.
        assertThat(user.getAvatarUrl()).isEqualTo("https://innovacar.app/uploads/avatars/1/custom.jpg");
    }

    @Test
    void emailAlreadyLinkedToDifferentGoogleAccount_isRejected() {
        Tenant tenant = activeTenant();
        User user = existingUser(tenant);
        user.setGoogleId("a-different-google-sub");
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticateGoogleUser(SUB, "user@example.com", true, "Test User", "Test", PICTURE))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("PROVIDER_MISMATCH"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void blockedUser_cannotLogInThroughGoogle() {
        Tenant tenant = activeTenant();
        User user = existingUser(tenant);
        user.setAccountEnabled(false);
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticateGoogleUser(SUB, "user@example.com", true, "Test User", "Test", PICTURE))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("ACCOUNT_BLOCKED"));
    }

    @Test
    void noTenantLinked_isRejected() {
        User user = existingUser(null);
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticateGoogleUser(SUB, "user@example.com", true, "Test User", "Test", PICTURE))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("TENANT_NOT_FOUND"));
    }

    @Test
    void suspendedTenant_cannotLogInThroughGoogle() {
        Tenant tenant = Tenant.builder().id(10L).name("Acme Rentals").status("SUSPENDED").build();
        User user = existingUser(tenant);
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticateGoogleUser(SUB, "user@example.com", true, "Test User", "Test", PICTURE))
                .isInstanceOf(GoogleAuthException.class)
                .satisfies(ex -> assertThat(((GoogleAuthException) ex).getErrorCode()).isEqualTo("ACCOUNT_SUSPENDED"));
    }

    @Test
    void twoFactorEnabledUser_getsChallengeInsteadOfTokens() {
        Tenant tenant = activeTenant();
        User user = existingUser(tenant);
        user.setTwoFactorEnabled(true);
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenProvider.generateChallengeToken(user)).thenReturn("challenge-token");

        var response = service.authenticateGoogleUser(SUB, "user@example.com", true, "Test User", "Test", PICTURE);

        assertThat(response.getTwoFactorRequired()).isTrue();
        assertThat(response.getTwoFactorMethod()).isEqualTo("TOTP");
        assertThat(response.getChallengeToken()).isEqualTo("challenge-token");
        verify(jwtTokenProvider, never()).generateToken(any(), any());
    }

    @Test
    void validNewUser_createsAgencyAndReturnsAdminRole() {
        when(userRepository.findByGoogleId(SUB)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        Tenant savedTenant = Tenant.builder().id(20L).name("Test User's Agency").build();
        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedTenant);
        User savedUser = User.builder().id(2L).email("user@example.com").role(com.carrental.entity.Role.ADMIN)
                .tenant(savedTenant).emailVerified(true).accountEnabled(true).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        stubAuthResponseCollaborators(savedUser, 77L);

        var response = service.authenticateGoogleUser(SUB, "user@example.com", true, "Test User", "Test", PICTURE);

        assertThat(response.getRole()).isEqualTo(com.carrental.entity.Role.ADMIN);
        assertThat(response.getTenantId()).isEqualTo(20L);
        verify(tenantRepository).save(any(Tenant.class));
        verify(emailService).sendWelcomeEmail(eq("user@example.com"), any());
        verify(userRepository).save(argThatMatchesNewGoogleUser());
    }

    private static User argThatEmailIsNot(String email) {
        return org.mockito.ArgumentMatchers.argThat(u -> !u.getEmail().equals(email));
    }

    private static User argThatMatchesNewGoogleUser() {
        return org.mockito.ArgumentMatchers.argThat(u ->
                u.getAuthProvider() == com.carrental.entity.AuthProvider.GOOGLE
                && PICTURE.equals(u.getAvatarUrl())
                && u.getLastLoginAt() != null);
    }
}
