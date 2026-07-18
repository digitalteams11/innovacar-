package com.carrental.service;

import com.carrental.dto.*;
import com.carrental.entity.*;
import com.carrental.entity.EmailOtpPurpose;
import com.carrental.exception.TwoFactorVerificationException;
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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic for authentication: signup, login, password reset,
 * email verification, token refresh, and registration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** How long a 6-digit email verification code stays valid — kept in sync with the email copy that quotes it. */
    private static final int EMAIL_VERIFICATION_CODE_EXPIRY_MINUTES = 10;

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
    private final SmtpMailService          smtpMailService;
    private final SessionService           sessionService;
    private final PasswordPolicyService    passwordPolicyService;
    private final DeviceSecurityService    deviceSecurityService;
    private final TwoFactorService         twoFactorService;
    private final EmailOtpService          emailOtpService;
    private final EmployeeRepository       employeeRepository;
    private final RolePermissionService    rolePermissionService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ── Signup (Tenant + Admin) ─────────────────────────────────────────────

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        passwordPolicyService.validate(request.getPassword());
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
        String email = normalizeEmail(request.getEmail());
        log.info("Login attempt for email: {}", email);
        Optional<User> userOpt = userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(email);
        boolean userFound = userOpt.isPresent();
        boolean accountEnabled = userOpt.map(User::isEnabled).orElse(false);
        boolean passwordMatches = userOpt.map(u -> passwordEncoder.matches(request.getPassword(), u.getPassword())).orElse(false);
        String role = userOpt.map(u -> u.getRole().name()).orElse("NONE");
        Long loginTenantId = userOpt.map(this::tenantId).orElse(null);

        log.info("User found: {}", userFound);
        log.info("Account enabled: {}", accountEnabled);
        log.info("Password matches: {}", passwordMatches);
        log.info("Role: {}", role);
        log.info("Tenant ID: {}", loginTenantId);

        // Check rate limiting
        if (rateLimitService.isLoginRateLimited(email) || rateLimitService.isIpRateLimited(ipAddress)) {
            log.warn("Login rate limited for email: {} from IP: {}", email, ipAddress);
            throw new LockedException("Too many failed attempts. Please try again later.");
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email, request.getPassword()));

            User user = (User) auth.getPrincipal();

            // Check if account is locked
            if (user.isLocked()) {
                rateLimitService.recordAttempt(email, ipAddress, false, userAgent,
                        user.getId(), tenantId(user), "Account locked");
                throw new LockedException("Account is temporarily locked. Please try again later.");
            }

            boolean totpEnabled     = Boolean.TRUE.equals(user.getTwoFactorEnabled());
            boolean emailOtpEnabled = Boolean.TRUE.equals(user.getEmailOtpEnabled());
            boolean needs2FA        = totpEnabled || emailOtpEnabled;

            if (needs2FA) {
                String deviceFingerprint = request.getDeviceFingerprint();
                boolean fingerprintPresent = deviceFingerprint != null && !deviceFingerprint.isBlank();
                var existingDevice = deviceSecurityService.findByFingerprint(user.getId(), deviceFingerprint);
                boolean trustedValid = existingDevice.map(com.carrental.entity.TrustedDevice::isActiveTrust).orElse(false);
                boolean trustedExpired = existingDevice
                        .map(d -> Boolean.TRUE.equals(d.getTrusted()) && d.getRevokedAt() == null
                                && d.getExpiresAt() != null && !LocalDateTime.now().isBefore(d.getExpiresAt()))
                        .orElse(false);
                boolean trustedRevoked = existingDevice.map(d -> d.getRevokedAt() != null).orElse(false);

                log.debug("[TRUSTED_DEVICE_DEBUG] userId={} has2FA={} trustedCookiePresent={} trustedTokenValid={} " +
                        "trustedDeviceExpired={} trustedDeviceRevoked={} requires2FA={} trustDeviceRequested={} " +
                        "trustedDeviceCreated={} expiresAt={}",
                        user.getId(), needs2FA, fingerprintPresent, trustedValid, trustedExpired, trustedRevoked,
                        !trustedValid, false, false, existingDevice.map(com.carrental.entity.TrustedDevice::getExpiresAt).orElse(null));

                // Check if this device has been previously trusted — skip 2FA if so
                if (trustedValid) {
                    log.info("[2FA_TRUSTED_DEVICE] Skipping 2FA for userId={} '{}' — trusted device",
                            user.getId(), email);
                } else {
                    boolean hasOtp      = request.getOtpCode() != null && !request.getOtpCode().isBlank();
                    boolean hasRecovery = request.getRecoveryCode() != null && !request.getRecoveryCode().isBlank();

                    if (!hasOtp && !hasRecovery) {
                        // First-leg: credentials OK but 2FA not yet verified → issue challenge token
                        Set<String> methods = new HashSet<>();
                        if (totpEnabled)     methods.add("AUTHENTICATOR");
                        if (emailOtpEnabled) methods.add("EMAIL");

                        String primaryMethod = totpEnabled ? "AUTHENTICATOR" : "EMAIL";
                        String challengeToken = jwtTokenProvider.generateChallengeToken(user);
                        return AuthResponse.builder()
                                .twoFactorRequired(true)
                                .twoFactorMethod(primaryMethod)
                                .availableTwoFactorMethods(methods)
                                .challengeToken(challengeToken)
                                .build();
                    }

                    // TOTP verification path (only when TOTP is enabled and code was provided)
                    if (totpEnabled && hasOtp && !hasRecovery) {
                        if (!twoFactorService.verify(user, request.getOtpCode())) {
                            rateLimitService.recordAttempt(email, ipAddress, false, userAgent,
                                    user.getId(), tenantId(user), "Invalid 2FA code");
                            throw new TwoFactorVerificationException(
                                    "The verification code is invalid or expired. Wait for a new code and try again.",
                                    "INVALID_2FA_CODE");
                        }
                    }
                    if (hasRecovery && !twoFactorService.verifyAndConsumeRecoveryCode(user, request.getRecoveryCode())) {
                        rateLimitService.recordAttempt(email, ipAddress, false, userAgent,
                                user.getId(), tenantId(user), "Invalid recovery code");
                        throw new TwoFactorVerificationException(
                                "Invalid or already used recovery code.",
                                "INVALID_2FA_CODE");
                    }
                }
            }

            deviceSecurityService.recordLogin(user, request.getDeviceFingerprint(),
                    request.getDeviceName(), ipAddress, userAgent);

            // Reset failed attempts on successful login
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            rateLimitService.recordAttempt(email, ipAddress, true, userAgent,
                    user.getId(), tenantId(user), null);

            log.info("User [id={}] '{}' (tenant {}) logged in from {}",
                    user.getId(), user.getEmail(), tenantId(user), ipAddress);

            return buildAuthResponse(user, ipAddress, userAgent);

        } catch (BadCredentialsException e) {
            // Record failed attempt
            // Increment failed attempts on user record
            Optional<User> failedUserOpt = userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(email);
            User failedUser = failedUserOpt.orElse(null);
            rateLimitService.recordAttempt(email, ipAddress, false, userAgent,
                    failedUser == null ? null : failedUser.getId(),
                    failedUser == null ? null : tenantId(failedUser),
                    "Invalid credentials");
            if (failedUserOpt.isPresent()) {
                User user = failedUserOpt.get();
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

    // Public registration. If tenantId is supplied, join that tenant as an employee.
    // If tenantId is absent, create a new agency automatically and make the user its admin.

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        passwordPolicyService.validate(request.getPassword());
        String email = normalizeEmail(request.getEmail());
        boolean createsNewTenant = request.getTenantId() == null;

        Tenant tenant;
        if (createsNewTenant) {
            if (userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(email).isPresent()) {
                // IllegalStateException (not IllegalArgumentException) so GlobalExceptionHandler
                // routes this to HTTP 409 CONFLICT with errorCode EMAIL_ALREADY_EXISTS, per the
                // registration response contract, instead of a generic 400.
                throw new IllegalStateException("Email already registered. Please sign in instead.");
            }
            tenant = createTenantForRegistration(request, email);
        } else {
            tenant = tenantRepository.findById(request.getTenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found with id: " + request.getTenantId()));
            if (userRepository.existsByEmailAndTenantId(email, tenant.getId())) {
                throw new IllegalStateException("Email already registered for this agency.");
            }
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(createsNewTenant ? Role.ADMIN : Role.EMPLOYEE)
                .tenant(tenant)
                .firstName(cleanNamePart(request.getFirstName()))
                .lastName(cleanNamePart(request.getLastName()))
                .emailVerified(false)
                .failedLoginAttempts(0)
                .build());

        log.info("Registered user [id={}] '{}' under tenant [id={}] role={}",
                user.getId(), user.getEmail(), tenant.getId(), user.getRole());

        sendVerificationEmail(user);
        emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());

        return buildAuthResponse(user);
    }

    // ── Refresh Token ────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse changeCurrentPassword(String currentPassword, String newPassword) {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (!(principal instanceof User user)) {
            throw new BadCredentialsException("Authenticated user not found");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        passwordPolicyService.replacePassword(user, newPassword);
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenService.revokeAllUserRefreshTokens(user.getId());
        sessionService.revokeAllUserSessions(user.getId());
        deviceSecurityService.revokeAllTrustedDevices(user.getId());
        return buildAuthResponse(user);
    }
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Optional<User> userOpt = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        if (userOpt.isEmpty()) {
            throw new com.carrental.exception.TokenRefreshException("Session expired. Please login again.");
        }

        User user = userOpt.get();

        // Revoke old refresh token
        refreshTokenService.revokeRefreshToken(request.getRefreshToken());
        sessionService.revokeSessionByTokenHash(RefreshTokenService.hashToken(request.getRefreshToken()));

        log.info("Refreshed tokens for user [id={}] '{}'", user.getId(), user.getEmail());

        return buildAuthResponse(user);
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeRefreshToken(refreshToken);
            sessionService.revokeSessionByTokenHash(RefreshTokenService.hashToken(refreshToken));
            log.info("User logged out, refresh token revoked");
        }
    }

    @Transactional
    public void logoutAllDevices(Long userId) {
        refreshTokenService.revokeAllUserRefreshTokens(userId);
        sessionService.revokeAllUserSessions(userId);
        deviceSecurityService.revokeAllTrustedDevices(userId);
        log.info("User [id={}] logged out from all devices", userId);
    }

    // ── Password Reset ───────────────────────────────────────────────────────

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request, String ipAddress, String userAgent) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        log.debug("[FORGOT_PASSWORD_DEBUG] endpointHit=true emailMasked={} userExists={}",
                maskEmail(normalizedEmail), userOpt.isPresent());
        if (userOpt.isEmpty()) {
            // Never reveal whether email exists
            log.info("[PWD_RESET] Reset requested for unknown email");
            simulateDelay();
            return;
        }

        User user = userOpt.get();

        // Rate limiting: max 3 code requests per user per 15 minutes, plus a 60-second
        // resend cooldown between requests. Both are enforced silently (same generic
        // success response as everywhere else in this method) — never surfaced as an
        // error — so a rate-limited request can't be distinguished from a normal one
        // and can't be used to probe account existence or request cadence.
        Optional<PasswordResetToken> mostRecent =
                passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        boolean cooldownActive = mostRecent.isPresent()
                && mostRecent.get().getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(60));
        long recentRequestCount = passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(
                user.getId(), LocalDateTime.now().minusMinutes(15));
        if (cooldownActive || recentRequestCount >= 3) {
            log.info("[PWD_RESET] Rate limit hit for user [id={}] recentRequestCount={} cooldownActive={}",
                    user.getId(), recentRequestCount, cooldownActive);
            simulateDelay();
            return;
        }

        // Invalidate all previous PENDING tokens for this user
        passwordResetTokenRepository.findAllByUserIdAndStatus(user.getId(), "PENDING")
                .forEach(t -> {
                    t.setStatus("EXPIRED");
                    passwordResetTokenRepository.save(t);
                });

        // Generate 6-digit code
        String rawCode = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(rawCode);

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .codeHash(codeHash)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .status("PENDING")
                .ipAddress(ipAddress)
                .userAgent(userAgent != null && userAgent.length() > 512
                        ? userAgent.substring(0, 512) : userAgent)
                .build());

        String userName = user.getFirstName() != null ? user.getFirstName() : user.getEmail();

        // Safe, non-secret snapshot of which SMTP config will actually be used —
        // logged before the send attempt so a misconfiguration is diagnosable
        // without ever touching the password or the generated code.
        SmtpMailService.PlatformSmtpDiagnostics diag = smtpMailService.describePlatformConfig();
        log.debug("[FORGOT_PASSWORD_EMAIL_DEBUG] targetEmail={} userExists=true smtpSettingsSource={} "
                        + "smtpHost={} smtpPort={} smtpUsernamePresent={} fromEmail={} fromName={} "
                        + "startTls={} passwordPresent={} sendAttempted=true",
                maskEmail(user.getEmail()), diag.source(), diag.host(), diag.port(), diag.usernamePresent(),
                diag.fromEmail(), diag.fromName(), diag.startTls(), diag.passwordPresent());

        SmtpMailService.SmtpResult result;
        try {
            result = emailService.sendPasswordResetCodeEmail(user.getEmail(), userName, rawCode, 10, user.getLanguage());
        } catch (Exception ex) {
            // Never let an unexpected exception here (template formatting, NPE, etc.)
            // masquerade as a generic SMTP failure — log the real cause and re-throw
            // as a clean errorCode the controller can map to a specific message.
            log.error("[FORGOT_PASSWORD_EMAIL_DEBUG] sendResult=false errorCode=EMAIL_SEND_FAILED "
                            + "exceptionClass={} exceptionMessage={}",
                    ex.getClass().getName(), ex.getMessage(), ex);
            throw new IllegalStateException("EMAIL_SEND_FAILED");
        }

        // Never log the OTP code or SMTP password — only the outcome.
        log.debug("[FORGOT_PASSWORD_EMAIL_DEBUG] targetEmail={} sendResult={} errorCode={}",
                maskEmail(user.getEmail()), result.sent(), result.errorCode());
        if (!result.sent()) {
            // Never claim the code was sent when it wasn't — surface a clean,
            // machine-readable error instead of a fake success response.
            log.error("[PWD_RESET] Failed to deliver code to user [id={}]: errorCode={} detail={}",
                    user.getId(), result.errorCode(), result.errorMessage());
            throw new IllegalStateException(result.errorCode() != null ? result.errorCode() : "EMAIL_SEND_FAILED");
        }
        log.info("[PWD_RESET] 6-digit code sent to user [id={}]", user.getId());
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        return (local.length() <= 3 ? local.charAt(0) + "***" : local.substring(0, 3) + "****") + "@" + domain;
    }

    /**
     * Verifies the 6-digit code and issues a short-lived resetSessionToken.
     * Returns the raw reset-session token (to be sent to client).
     */
    @Transactional
    public String verifyResetCode(String email, String code) {
        Optional<User> userOpt = userRepository.findByEmail(normalizeEmail(email));
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("INVALID_CODE");
        }
        User user = userOpt.get();

        PasswordResetToken token = passwordResetTokenRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING")
                .orElseThrow(() -> new IllegalArgumentException("INVALID_CODE"));

        if (token.isExpired()) {
            token.setStatus("EXPIRED");
            passwordResetTokenRepository.save(token);
            throw new IllegalArgumentException("CODE_EXPIRED");
        }

        if (token.getAttempts() >= 5) {
            token.setStatus("EXPIRED");
            passwordResetTokenRepository.save(token);
            throw new IllegalArgumentException("TOO_MANY_ATTEMPTS");
        }

        if (!passwordEncoder.matches(code.trim(), token.getCodeHash())) {
            token.setAttempts(token.getAttempts() + 1);
            passwordResetTokenRepository.save(token);
            int remaining = 5 - token.getAttempts();
            if (remaining <= 0) {
                token.setStatus("EXPIRED");
                passwordResetTokenRepository.save(token);
                throw new IllegalArgumentException("TOO_MANY_ATTEMPTS");
            }
            throw new IllegalArgumentException("INVALID_CODE");
        }

        // Code correct — generate resetSessionToken
        String rawSessionToken = generateSecureToken();
        String sessionTokenHash = RefreshTokenService.hashToken(rawSessionToken);

        token.setResetSessionTokenHash(sessionTokenHash);
        token.setResetSessionExpiresAt(LocalDateTime.now().plusMinutes(10));
        // Keep status PENDING — will be set USED in confirmPasswordReset
        passwordResetTokenRepository.save(token);

        log.info("[PWD_RESET] Code verified for user [id={}], session token issued", user.getId());
        return rawSessionToken;
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        String sessionTokenHash = RefreshTokenService.hashToken(request.getResetSessionToken());

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByResetSessionTokenHash(sessionTokenHash)
                .orElseThrow(() -> new IllegalArgumentException("INVALID_SESSION_TOKEN"));

        if (!"PENDING".equals(resetToken.getStatus()) || resetToken.isResetSessionExpired()) {
            throw new IllegalArgumentException("INVALID_SESSION_TOKEN");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        passwordPolicyService.replacePassword(user, request.getNewPassword());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setStatus("USED");
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Revoke all sessions and trusted devices
        refreshTokenService.revokeAllUserRefreshTokens(user.getId());
        sessionService.revokeAllUserSessions(user.getId());
        deviceSecurityService.revokeAllTrustedDevices(user.getId());

        String userName = user.getFirstName() != null ? user.getFirstName() : user.getEmail();
        emailService.sendPasswordChangedEmail(user.getEmail(), userName);

        log.info("[PWD_RESET] Password reset completed for user [id={}]", user.getId());
    }

    /** Constant-time delay so email-not-found paths take the same time as found paths. */
    private void simulateDelay() {
        try { Thread.sleep(200 + new SecureRandom().nextInt(100)); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
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

    /**
     * Code-based email verification — sends a 6-digit code to the authenticated user's email.
     * Requires the user to be already authenticated (not yet email-verified).
     */
    @Transactional
    public void sendEmailVerificationCode(User user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("ALREADY_VERIFIED");
        }

        // Reuse or create EmailVerificationToken row for this user
        EmailVerificationToken token = emailVerificationTokenRepository
                .findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElse(null);

        // Generate 6-digit code
        String rawCode = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(rawCode);

        if (token == null) {
            token = EmailVerificationToken.builder()
                    .userId(user.getId())
                    .tokenHash("CODE_FLOW_" + UUID.randomUUID())
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .used(false)
                    .build();
        }

        token.setVerificationCodeHash(codeHash);
        token.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(EMAIL_VERIFICATION_CODE_EXPIRY_MINUTES));
        token.setVerificationCodeAttempts(0);
        emailVerificationTokenRepository.save(token);

        String displayName = (user.getFirstName() != null && !user.getFirstName().isBlank())
                ? user.getFirstName() : user.getEmail();
        SmtpMailService.SmtpResult result = emailService.sendEmailVerificationCodeEmail(
                user.getEmail(), displayName, rawCode, EMAIL_VERIFICATION_CODE_EXPIRY_MINUTES);
        if (!result.sent()) {
            // Never claim the code was sent when it wasn't.
            log.error("[EMAIL_VERIFY_CODE] Failed to deliver code to user [id={}]: errorCode={} detail={}",
                    user.getId(), result.errorCode(), result.errorMessage());
            throw new IllegalStateException(result.errorCode() != null ? result.errorCode() : "EMAIL_SEND_FAILED");
        }
        log.info("[EMAIL_VERIFY_CODE] Code sent to user [id={}]", user.getId());
    }

    /**
     * Code-based email verification — verifies the 6-digit code for an authenticated user.
     */
    @Transactional
    public void verifyEmailCode(User user, String code) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("Email is already verified.");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("INVALID_CODE");
        }

        EmailVerificationToken token = emailVerificationTokenRepository
                .findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("CODE_NOT_FOUND"));

        if (token.getVerificationCodeHash() == null) {
            throw new IllegalArgumentException("CODE_NOT_FOUND");
        }
        if (token.getVerificationCodeAttempts() != null && token.getVerificationCodeAttempts() >= 5) {
            throw new IllegalArgumentException("TOO_MANY_ATTEMPTS");
        }
        if (token.isCodeExpired()) {
            throw new IllegalArgumentException("CODE_EXPIRED");
        }

        token.setVerificationCodeAttempts((token.getVerificationCodeAttempts() == null ? 0 : token.getVerificationCodeAttempts()) + 1);

        if (!passwordEncoder.matches(code, token.getVerificationCodeHash())) {
            emailVerificationTokenRepository.save(token);
            throw new IllegalArgumentException("INVALID_CODE");
        }

        // Code valid — mark email as verified
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setVerificationCodeHash(null);
        token.setUsed(true);
        emailVerificationTokenRepository.save(token);

        log.info("[EMAIL_VERIFY_CODE] Email verified via code for user [id={}] '{}'", user.getId(), user.getEmail());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    // ── 2FA second-leg verification ──────────────────────────────────────────

    /**
     * Validates the short-lived challenge token issued by the first login leg,
     * verifies either a TOTP code or a recovery code, and returns a full token pair.
     */
    @Transactional
    public AuthResponse verifyTwoFactor(
            String challengeToken, String code, String recoveryCode,
            String deviceFingerprint, String deviceName, Boolean trustDevice,
            String ipAddress, String userAgent) {

        if (challengeToken == null || challengeToken.isBlank()) {
            throw new TwoFactorVerificationException(
                    "Challenge token is required. Please log in again.", "EXPIRED_2FA_CHALLENGE");
        }
        if (!jwtTokenProvider.validateToken(challengeToken)
                || !jwtTokenProvider.isChallengeToken(challengeToken)) {
            throw new TwoFactorVerificationException(
                    "Your login session has expired. Please log in again.", "EXPIRED_2FA_CHALLENGE");
        }

        String email    = jwtTokenProvider.getEmail(challengeToken);
        Long   tenantId = jwtTokenProvider.getTenantId(challengeToken);

        User user = userRepository.findByEmailAndTenantId(email, tenantId)
                .orElseThrow(() -> new TwoFactorVerificationException("User not found.", "EXPIRED_2FA_CHALLENGE"));

        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException("Two-factor authentication is not enabled for this account.");
        }

        boolean hasCode     = code         != null && !code.isBlank();
        boolean hasRecovery = recoveryCode != null && !recoveryCode.isBlank();

        if (!hasCode && !hasRecovery) {
            throw new TwoFactorVerificationException(
                    "A verification code or recovery code is required.", "INVALID_2FA_CODE");
        }

        log.debug("[2FA_VERIFY_DEBUG] userId={} email={} hasSecret={} codeLength={} serverTime={} " +
                "timeWindow=1 challengeValid=true trustedDeviceRequested={}",
                user.getId(), email,
                user.getTwoFactorSecretEncrypted() != null,
                hasCode ? (code != null ? code.trim().length() : 0) : 0,
                System.currentTimeMillis() / 1000,
                Boolean.TRUE.equals(trustDevice));

        if (hasCode && !twoFactorService.verify(user, code)) {
            rateLimitService.recordAttempt(email, ipAddress, false, userAgent,
                    user.getId(), tenantId(user), "Invalid 2FA code");
            log.debug("[2FA_VERIFY_DEBUG] userId={} result=FAILED (invalid TOTP code)", user.getId());
            throw new TwoFactorVerificationException(
                    "The verification code is invalid or expired. Wait for a new code and try again.",
                    "INVALID_2FA_CODE");
        }
        if (hasRecovery && !twoFactorService.verifyAndConsumeRecoveryCode(user, recoveryCode)) {
            rateLimitService.recordAttempt(email, ipAddress, false, userAgent,
                    user.getId(), tenantId(user), "Invalid recovery code");
            log.debug("[2FA_VERIFY_DEBUG] userId={} result=FAILED (invalid recovery code)", user.getId());
            throw new TwoFactorVerificationException(
                    "Invalid or already used recovery code.",
                    "INVALID_2FA_CODE");
        }

        log.debug("[2FA_VERIFY_DEBUG] userId={} result=SUCCESS", user.getId());

        // Trust the device if requested — creates a 30-day trusted record
        boolean trustRequested = Boolean.TRUE.equals(trustDevice);
        boolean fingerprintPresent = deviceFingerprint != null && !deviceFingerprint.isBlank();
        boolean trustCreated = trustRequested && fingerprintPresent;
        LocalDateTime newExpiresAt = null;
        if (trustRequested) {
            deviceSecurityService.trustDevice(user, deviceFingerprint, deviceName, ipAddress, userAgent);
            if (trustCreated) newExpiresAt = LocalDateTime.now().plusDays(30);
        }

        deviceSecurityService.recordLogin(user, deviceFingerprint, deviceName, ipAddress, userAgent);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        rateLimitService.recordAttempt(email, ipAddress, true, userAgent,
                user.getId(), tenantId(user), null);

        log.debug("[TRUSTED_DEVICE_DEBUG] userId={} has2FA=true trustedCookiePresent={} trustedTokenValid=false " +
                "trustedDeviceExpired=false trustedDeviceRevoked=false requires2FA=false trustDeviceRequested={} " +
                "trustedDeviceCreated={} expiresAt={}",
                user.getId(), fingerprintPresent, trustRequested, trustCreated, newExpiresAt);
        log.info("[2FA_VERIFY] userId={} email={} (tenant {}) trustDevice={}",
                user.getId(), email, tenantId, trustRequested);
        return buildAuthResponse(user, ipAddress, userAgent);
    }

    /**
     * Sends an Email OTP during the login 2FA challenge leg.
     * Validates the challenge token before sending.
     */
    @Transactional
    public String sendLoginEmailOtp(String challengeToken, String ipAddress, String userAgent) {
        if (challengeToken == null || !jwtTokenProvider.validateToken(challengeToken)
                || !jwtTokenProvider.isChallengeToken(challengeToken)) {
            throw new TwoFactorVerificationException(
                    "Your login session has expired. Please log in again.", "EXPIRED_2FA_CHALLENGE");
        }
        String email    = jwtTokenProvider.getEmail(challengeToken);
        Long   tenantId = jwtTokenProvider.getTenantId(challengeToken);
        User user = userRepository.findByEmailAndTenantId(email, tenantId)
                .orElseThrow(() -> new TwoFactorVerificationException("User not found.", "EXPIRED_2FA_CHALLENGE"));
        if (!Boolean.TRUE.equals(user.getEmailOtpEnabled())) {
            throw new IllegalStateException("Email OTP is not enabled for this account.");
        }
        emailOtpService.sendCode(user, EmailOtpPurpose.LOGIN_2FA, ipAddress, userAgent);
        return emailOtpService.maskEmail(user.getEmail());
    }

    /**
     * Verifies an Email OTP during the login 2FA challenge leg.
     * Returns a full token pair on success.
     */
    @Transactional
    public AuthResponse verifyTwoFactorEmail(
            String challengeToken, String code,
            String deviceFingerprint, String deviceName, Boolean trustDevice,
            String ipAddress, String userAgent) {

        if (challengeToken == null || !jwtTokenProvider.validateToken(challengeToken)
                || !jwtTokenProvider.isChallengeToken(challengeToken)) {
            throw new TwoFactorVerificationException(
                    "Your login session has expired. Please log in again.", "EXPIRED_2FA_CHALLENGE");
        }

        String email    = jwtTokenProvider.getEmail(challengeToken);
        Long   tenantId = jwtTokenProvider.getTenantId(challengeToken);
        User user = userRepository.findByEmailAndTenantId(email, tenantId)
                .orElseThrow(() -> new TwoFactorVerificationException("User not found.", "EXPIRED_2FA_CHALLENGE"));

        if (!Boolean.TRUE.equals(user.getEmailOtpEnabled())) {
            throw new IllegalStateException("Email OTP is not enabled for this account.");
        }

        try {
            emailOtpService.verifyCode(user.getId(), EmailOtpPurpose.LOGIN_2FA, code);
        } catch (IllegalStateException e) {
            String msg = switch (e.getMessage()) {
                case "EMAIL_OTP_EXPIRED"           -> "The code has expired. Request a new one.";
                case "EMAIL_OTP_TOO_MANY_ATTEMPTS" -> "Too many incorrect attempts. Request a new code.";
                default                            -> "Invalid verification code. Please try again.";
            };
            throw new TwoFactorVerificationException(msg, e.getMessage());
        }

        boolean trustRequested = Boolean.TRUE.equals(trustDevice);
        boolean fingerprintPresent = deviceFingerprint != null && !deviceFingerprint.isBlank();
        boolean trustCreated = trustRequested && fingerprintPresent;
        LocalDateTime newExpiresAt = null;
        if (trustRequested) {
            deviceSecurityService.trustDevice(user, deviceFingerprint, deviceName, ipAddress, userAgent);
            if (trustCreated) newExpiresAt = LocalDateTime.now().plusDays(30);
        }
        deviceSecurityService.recordLogin(user, deviceFingerprint, deviceName, ipAddress, userAgent);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        rateLimitService.recordAttempt(email, ipAddress, true, userAgent, user.getId(), tenantId(user), null);

        log.debug("[TRUSTED_DEVICE_DEBUG] userId={} has2FA=true trustedCookiePresent={} trustedTokenValid=false " +
                "trustedDeviceExpired=false trustedDeviceRevoked=false requires2FA=false trustDeviceRequested={} " +
                "trustedDeviceCreated={} expiresAt={}",
                user.getId(), fingerprintPresent, trustRequested, trustCreated, newExpiresAt);
        log.info("[2FA_EMAIL_VERIFY] userId={} email={} (tenant {}) trustDevice={}",
                user.getId(), email, tenantId, trustRequested);
        return buildAuthResponse(user, ipAddress, userAgent);
    }

    private AuthResponse buildAuthResponse(User user) {
        return buildAuthResponse(user, null, null);
    }

    private AuthResponse buildAuthResponse(User user, String ipAddress, String userAgent) {
        Long tenantId = tenantId(user);
        String tenantName = user.getTenant() != null ? user.getTenant().getName() : null;
        String verificationStatus = user.getTenant() != null ? user.getTenant().getVerificationStatus() : null;
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        refreshTokenService.saveRefreshToken(user.getId(), refreshToken, ipAddress, userAgent);
        UserSession session = sessionService.createSession(
                user.getId(),
                RefreshTokenService.hashToken(refreshToken),
                ipAddress,
                userAgent,
                jwtTokenProvider.getRefreshExpirationMs() / 60000
        );
        String accessToken = jwtTokenProvider.generateToken(user, session.getId());

        Set<String> availableMethods = new HashSet<>();
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled()))  availableMethods.add("AUTHENTICATOR");
        if (Boolean.TRUE.equals(user.getEmailOtpEnabled()))   availableMethods.add("EMAIL");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .roleCode(user.getRole() == null ? null : user.getRole().name())
                .tenantId(tenantId)
                .employeeId(employeeRepository.findByUserId(user.getId()).map(Employee::getId).orElse(null))
                .permissions(rolePermissionService.permissionsFor(user))
                .tenantName(tenantName)
                .emailVerified(user.getEmailVerified())
                .passwordExpired(!user.isCredentialsNonExpired())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .verificationStatus(verificationStatus)
                .twoFactorRequired(false)
                .twoFactorMethod(user.getTwoFactorMethod() == null
                        ? null : user.getTwoFactorMethod().name())
                .availableTwoFactorMethods(availableMethods.isEmpty() ? null : availableMethods)
                .emailOtpEnabled(Boolean.TRUE.equals(user.getEmailOtpEnabled()))
                .build();
    }


    private Tenant createTenantForRegistration(RegisterRequest request, String email) {
        String baseName = buildAgencyName(request);
        String tenantName = uniqueTenantName(baseName);
        String tenantEmail = uniqueTenantEmail(email);
        // Every new agency gets exactly one calendar month of free trial, starting
        // from account creation. subscriptionEndDate is deliberately left null here —
        // it means "paid-plan renewal date" and populating it with the trial end date
        // caused the billing UI to show a stale "Renews on ..." line once the tenant's
        // status ever drifted away from "TRIAL" (e.g. after a block/unblock cycle).
        LocalDate today = LocalDate.now();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(tenantName)
                .email(tenantEmail)
                .subscriptionActive(true)
                .trialStartDate(today)
                .trialEndDate(today.plusMonths(Tenant.TRIAL_PERIOD_MONTHS))
                .planName("Trial")
                .status("TRIAL")
                .verificationStatus("PENDING_VERIFICATION")
                .build());
        return tenant;
    }

    private String buildAgencyName(RegisterRequest request) {
        String fullName = (cleanNamePart(request.getFirstName()) + " " + cleanNamePart(request.getLastName())).trim();
        return fullName.isBlank() ? "New Agency" : fullName + " Agency";
    }

    private String uniqueTenantName(String baseName) {
        String candidate = baseName;
        int suffix = 2;
        while (tenantRepository.existsByName(candidate)) {
            candidate = baseName + " " + suffix++;
        }
        return candidate;
    }

    private String uniqueTenantEmail(String email) {
        if (!tenantRepository.existsByEmail(email)) {
            return email;
        }
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        String domain = at > 0 ? email.substring(at + 1) : "tenant.local";
        int suffix = 2;
        String candidate;
        do {
            candidate = local + "+agency" + suffix++ + "@" + domain;
        } while (tenantRepository.existsByEmail(candidate));
        return candidate;
    }

    private String cleanNamePart(String value) {
        return value == null ? "" : value.trim();
    }

    private Long tenantId(User user) {
        return user != null && user.getTenant() != null ? user.getTenant().getId() : null;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

