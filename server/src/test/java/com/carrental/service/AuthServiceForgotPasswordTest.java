package com.carrental.service;

import com.carrental.dto.PasswordResetRequest;
import com.carrental.entity.PasswordResetToken;
import com.carrental.entity.User;
import com.carrental.repository.*;
import com.carrental.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the forgot-password rate limiting added to
 * {@link AuthService#requestPasswordReset}: max 3 code requests per user per
 * 15 minutes, plus a 60-second resend cooldown — both enforced silently
 * (identical generic outcome to every other branch of this method) so a
 * rate-limited request is indistinguishable from a normal one.
 */
class AuthServiceForgotPasswordTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
    private final EmailVerificationTokenRepository emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final SmtpMailService smtpMailService = mock(SmtpMailService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
    private final DeviceSecurityService deviceSecurityService = mock(DeviceSecurityService.class);
    private final TwoFactorService twoFactorService = mock(TwoFactorService.class);
    private final EmailOtpService emailOtpService = mock(EmailOtpService.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final RolePermissionService rolePermissionService = mock(RolePermissionService.class);

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                tenantRepository, userRepository, refreshTokenRepository, passwordResetTokenRepository,
                emailVerificationTokenRepository, passwordEncoder, jwtTokenProvider, authenticationManager,
                rateLimitService, refreshTokenService, emailService, smtpMailService, sessionService,
                passwordPolicyService, deviceSecurityService, twoFactorService, emailOtpService,
                employeeRepository, rolePermissionService);

        user = User.builder().id(1L).email("user@example.com").firstName("Jane").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findAllByUserIdAndStatus(1L, "PENDING")).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");
        when(smtpMailService.describePlatformConfig()).thenReturn(
                new SmtpMailService.PlatformSmtpDiagnostics(true, "DATABASE", "smtp.example.com", 587, true,
                        "noreply@innovacar.app", "Innovacar", true, true));
    }

    private PasswordResetRequest request() {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setEmail("user@example.com");
        return req;
    }

    @Test
    void withinCooldownWindow_skipsCodeGenerationAndEmail() {
        PasswordResetToken recent = PasswordResetToken.builder()
                .userId(1L).createdAt(LocalDateTime.now().minusSeconds(30)).status("PENDING").build();
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(recent));
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(1L);

        authService.requestPasswordReset(request(), "127.0.0.1", "test-agent");

        verify(passwordResetTokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void exceedingThreeRequestsIn15Minutes_skipsCodeGenerationAndEmail() {
        PasswordResetToken recent = PasswordResetToken.builder()
                .userId(1L).createdAt(LocalDateTime.now().minusMinutes(5)).status("PENDING").build();
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(recent));
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(3L);

        authService.requestPasswordReset(request(), "127.0.0.1", "test-agent");

        verify(passwordResetTokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void firstRequest_generatesCodeAndSendsEmail() {
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(0L);
        when(emailService.sendPasswordResetCodeEmail(anyString(), anyString(), anyString(), eq(10), any()))
                .thenReturn(new SmtpMailService.SmtpResult(true, "smtp", null, null, null));

        authService.requestPasswordReset(request(), "127.0.0.1", "test-agent");

        verify(passwordResetTokenRepository).save(argThat(saved ->
                "PENDING".equals(saved.getStatus()) && saved.getUserId().equals(1L)));
        verify(emailService).sendPasswordResetCodeEmail(eq("user@example.com"), eq("Jane"), anyString(), eq(10), any());
    }

    @Test
    void unknownEmail_neverTouchesResetTokenRepository() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        PasswordResetRequest req = new PasswordResetRequest();
        req.setEmail("ghost@example.com");

        authService.requestPasswordReset(req, "127.0.0.1", "test-agent");

        verifyNoInteractions(passwordResetTokenRepository);
        verifyNoInteractions(emailService);
    }
}
