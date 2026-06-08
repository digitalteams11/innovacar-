package com.carrental.service;

import com.carrental.dto.*;
import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for authentication: signup, login, password reset,
 * email verification, token refresh, and registration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository         tenantRepository;
    private final UserRepository           userRepository;
    private final RefreshTokenRepository   refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder          passwordEncoder;
    private final JwtTokenProvider         jwtTokenProvider;
    private final AuthenticationManager    authenticationManager;
    private final RateLimitService         rateLimitService;
    private final RefreshTokenService      refreshTokenService;
    private final EmailService             emailService;
    private final SessionService           sessionService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ── Signup (Tenant + Admin) ─────────────────────────────────────────────

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (tenantRepository.existsByName(request.getTenantName())) {
            throw new IllegalArgumentException(
                "Tenant name already in use: " + request.getTenantName());
        }
        if (tenantRepository.existsByEmail(request.getTenantEmail())) {
            throw new IllegalArgumentException(
                "Tenant email already in use: " + request.getTenantEmail());
        }

        LocalDate endDate = request.getSubscriptionEndDate() != null
                ? request.getSubscriptionEndDate()
                : LocalDate.now().plusYears(1);

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(request.getTenantName())
                .email(request.getTenantEmail())
                .subscriptionActive(true)
                .subscriptionEndDate(endDate)
                .build());

        if (userRepository.existsByEmailAndTenantId(request.getAdminEmail(), tenant.getId())) {
            throw new IllegalArgumentException("Admin email already registered for this tenant.");
        }

        User admin = userRepository.save(User.builder()
                .email(request.getAdminEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ADMIN)
                .tenant(tenant)
                .emailVerified(false)
                .failedLoginAttempts(0)
                .build());

        log.info("Created tenant [id={}] '{}' with admin [id={}] '{}'",
                tenant.getId(), tenant.getName(), admin.getId(), admin.getEmail());

        // Send verification email
        sendVerificationEmail(admin);
        emailService.sendWelcomeEmail(admin.getEmail(), null);

        return buildAuthResponse(admin);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // Check rate limiting
        if (rateLimitService.isLoginRateLimited(request.getEmail())) {
            log.warn("Login rate limited for email: {} from IP: {}", request.getEmail(), ipAddress);
            throw new LockedException("Too many failed attempts. Please try again later.");
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));

            User user = (User) auth.getPrincipal();

            // Check if account is locked
            if (user.isLocked()) {
                rateLimitService.recordAttempt(request.getEmail(), ipAddress, false, userAgent);
                throw new LockedException("Account is temporarily locked. Please try again later.");
            }

            // Reset failed attempts on successful login
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            rateLimitService.recordAttempt(request.getEmail(), ipAddress, true, userAgent);

            log.info("User [id={}] '{}' (tenant {}) logged in from {}",
                    user.getId(), user.getEmail(), user.getTenant().getId(), ipAddress);

            return buildAuthResponse(user);

        } catch (BadCredentialsException e) {
            // Record failed attempt
            rateLimitService.recordAttempt(request.getEmail(), ipAddress, false, userAgent);

            // Increment failed attempts on user record
            Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
                user.setFailedLoginAttempts(attempts);

                // Lock account after 5 failed attempts
                if (attempts >= 5) {
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
                    log.warn("Account locked for user [id={}] '{}' after {} failed attempts",
                            user.getId(), user.getEmail(), attempts);
                }
                userRepository.save(user);
            }

            throw e;
        }
    }

    // ── Register (User under existing tenant) ────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenant.getId())) {
            throw new IllegalArgumentException("Email already registered for this tenant.");
        }

        User user = userRepository.save(User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.EMPLOYEE)
                .tenant(tenant)
                .emailVerified(false)
                .failedLoginAttempts(0)
                .build());

        log.info("Registered user [id={}] '{}' under tenant [id={}]",
                user.getId(), user.getEmail(), tenant.getId());

        sendVerificationEmail(user);

        return buildAuthResponse(user);
    }

    // ── Refresh Token ────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Optional<User> userOpt = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        User user = userOpt.get();

        // Revoke old refresh token
        refreshTokenService.revokeRefreshToken(request.getRefreshToken());

        log.info("Refreshed tokens for user [id={}] '{}'", user.getId(), user.getEmail());

        return buildAuthResponse(user);
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeRefreshToken(refreshToken);
            log.info("User logged out, refresh token revoked");
        }
    }

    @Transactional
    public void logoutAllDevices(Long userId) {
        refreshTokenService.revokeAllUserRefreshTokens(userId);
        sessionService.revokeAllUserSessions(userId);
        log.info("User [id={}] logged out from all devices", userId);
    }

    // ── Password Reset ───────────────────────────────────────────────────────

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            // Don't reveal whether email exists
            log.info("Password reset requested for non-existent email: {}", request.getEmail());
            return;
        }

        User user = userOpt.get();

        // Clean up old tokens
        passwordResetTokenRepository.deleteByUserId(user.getId());

        String rawToken = generateSecureToken();
        String tokenHash = RefreshTokenService.hashToken(rawToken);

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build());

        emailService.sendPasswordResetEmail(user.getEmail(), rawToken, frontendUrl);
        log.info("Password reset token generated for user [id={}] '{}'", user.getId(), user.getEmail());
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        String tokenHash = RefreshTokenService.hashToken(request.getToken());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.isExpired()) {
            throw new IllegalArgumentException("Reset token has already been used or expired");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Revoke all refresh tokens for security
        refreshTokenService.revokeAllUserRefreshTokens(user.getId());

        log.info("Password reset completed for user [id={}] '{}'", user.getId(), user.getEmail());
    }

    // ── Email Verification ───────────────────────────────────────────────────

    @Transactional
    public void sendVerificationEmail(User user) {
        // Clean up old tokens
        emailVerificationTokenRepository.deleteByUserId(user.getId());

        String rawToken = generateSecureToken();
        String tokenHash = RefreshTokenService.hashToken(rawToken);

        emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build());

        emailService.sendVerificationEmail(user.getEmail(), rawToken, frontendUrl);
        log.info("Verification email sent to user [id={}] '{}'", user.getId(), user.getEmail());
    }

    @Transactional
    public void resendVerificationEmail(ResendVerificationRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            return; // Don't reveal existence
        }

        User user = userOpt.get();
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return; // Already verified
        }

        sendVerificationEmail(user);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        String tokenHash = RefreshTokenService.hashToken(request.getToken());

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        if (Boolean.TRUE.equals(verificationToken.getUsed()) || verificationToken.isExpired()) {
            throw new IllegalArgumentException("Verification token has already been used or expired");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        log.info("Email verified for user [id={}] '{}'", user.getId(), user.getEmail());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        refreshTokenService.saveRefreshToken(user.getId(), refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenant().getId())
                .tenantName(user.getTenant().getName())
                .emailVerified(user.getEmailVerified())
                .build();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
