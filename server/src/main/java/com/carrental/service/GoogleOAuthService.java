package com.carrental.service;

import com.carrental.dto.AuthResponse;
import com.carrental.entity.AuthProvider;
import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.entity.UserSession;
import com.carrental.exception.GoogleAuthException;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves a verified Google identity (subject/email/profile claims) into an
 * Innovacar account — links to or creates a {@link User}, then issues the
 * exact same JWT pair {@link com.carrental.service.AuthService} issues for
 * password login. No duplicated authentication logic: this is the single
 * place OAuth login and password login converge on the same token issuance.
 *
 * <p>Token verification itself is NOT this class's job — it's handled
 * upstream by Spring Security's OIDC login machinery (see
 * {@code com.carrental.security.oauth2.CustomOidcUserService}), which
 * validates the ID token's signature, issuer and audience against Google's
 * published JWKS before this service ever runs. This class only receives
 * already-trusted claims and performs Innovacar's own business rules
 * (account linking/creation, blocked/suspended checks, 2FA).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final SessionService sessionService;
    private final EmailService emailService;

    /**
     * Resolves an already-verified Google identity into an authenticated
     * Innovacar session. If a user already exists (matched by Google
     * subject, then by email), logs them in and links the Google identity
     * if it wasn't already. Otherwise creates a new tenant + account with
     * the default {@link Role#ADMIN} (agency owner) role.
     *
     * @param sub           Google's stable subject identifier ({@code sub} claim) — the durable Google account ID.
     * @param email         the account email Google returned.
     * @param emailVerified Google's own {@code email_verified} claim.
     * @param name          full display name, may be null.
     * @param givenName     first name, may be null.
     * @param picture       avatar URL, may be null.
     */
    public AuthResponse authenticateGoogleUser(String sub, String email, boolean emailVerified,
                                                String name, String givenName, String picture) {
        if (!StringUtils.hasText(sub)) {
            throw new GoogleAuthException("Your Google sign-in could not be verified. Please try again.", "GOOGLE_TOKEN_INVALID");
        }
        if (!StringUtils.hasText(email)) {
            throw new GoogleAuthException("Your Google account has no email address on file.", "GOOGLE_EMAIL_MISSING");
        }
        if (!emailVerified) {
            throw new GoogleAuthException("Your Google account's email address is not verified.", "GOOGLE_EMAIL_NOT_VERIFIED");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        // Check if user already exists by Google ID
        Optional<User> existingByGoogle = userRepository.findByGoogleId(sub);
        if (existingByGoogle.isPresent()) {
            return authenticateExistingUser(existingByGoogle.get(), picture);
        }

        // Check if user exists by email (link Google account) — never creates a
        // duplicate account for an email that already has one. Role, tenant,
        // password, subscription and block/suspension state are all left
        // untouched — only the Google identity and email-verified flag change.
        Optional<User> existingByEmail = userRepository.findByEmail(normalizedEmail);
        if (existingByEmail.isPresent()) {
            User user = existingByEmail.get();
            // Provider mismatch: this email already belongs to a different Google
            // account (a different `sub`) than the one signing in right now —
            // never silently re-link a second Google identity onto an account.
            if (StringUtils.hasText(user.getGoogleId()) && !user.getGoogleId().equals(sub)) {
                throw new GoogleAuthException(
                        "This email is already linked to a different Google account.", "PROVIDER_MISMATCH");
            }
            user.setGoogleId(sub);
            user.setEmailVerified(true);
            user.setAuthProvider(user.getAuthProvider() == AuthProvider.GOOGLE
                    ? AuthProvider.GOOGLE
                    : AuthProvider.LOCAL_AND_GOOGLE);
            if (!StringUtils.hasText(user.getAvatarUrl()) && StringUtils.hasText(picture)) {
                user.setAvatarUrl(picture);
            }
            userRepository.save(user);
            log.info("Linked Google account to existing user: {}", user.getEmail());
            return authenticateExistingUser(user, picture);
        }

        // Create new tenant + owner user for Google signup
        return createUserFromGoogle(sub, normalizedEmail, name, givenName, picture);
    }

    /** Rejects blocked/suspended/locked accounts before ever issuing a token — the
     *  password-login path enforces these through Spring Security's AuthenticationManager;
     *  Google OAuth bypasses that manager entirely, so it must check explicitly instead. */
    private AuthResponse authenticateExistingUser(User user, String picture) {
        if (!user.isEnabled()) {
            throw new GoogleAuthException("Your account has been blocked. Please contact support.", "ACCOUNT_BLOCKED");
        }
        if (user.isLocked()) {
            throw new GoogleAuthException("Your account is temporarily locked. Please try again later.", "ACCOUNT_BLOCKED");
        }
        Tenant tenant = user.getTenant();
        if (tenant == null) {
            throw new GoogleAuthException("No agency is linked to this account.", "TENANT_NOT_FOUND");
        }
        if (tenant.isAccountBlocked()) {
            throw new GoogleAuthException("Your agency account is suspended. Please contact support.", "ACCOUNT_SUSPENDED");
        }
        user.setLastLoginAt(LocalDateTime.now());
        // Never overwrite a user's own custom avatar with Google's photo — only
        // fill it in when the account has no avatar of its own yet.
        if (!StringUtils.hasText(user.getAvatarUrl()) && StringUtils.hasText(picture)) {
            user.setAvatarUrl(picture);
        }
        userRepository.save(user);
        return buildAuthResponseOrChallenge(user);
    }

    private AuthResponse createUserFromGoogle(String sub, String normalizedEmail, String name, String givenName, String picture) {
        // Create tenant
        String tenantName = StringUtils.hasText(name) ? name + "'s Agency" : "New Agency";
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(tenantName)
                .email(normalizedEmail)
                .subscriptionActive(true)
                .subscriptionEndDate(LocalDate.now().plusYears(1))
                .build());

        // Create user — default role is ADMIN (agency owner) unless business
        // logic elsewhere reassigns it; Google OAuth never creates EMPLOYEE/
        // ACCOUNTANT/etc. accounts, only the owning admin of a brand-new agency.
        User user = userRepository.save(User.builder()
                .email(normalizedEmail)
                .password("GOOGLE_OAUTH_" + System.currentTimeMillis()) // Random placeholder — not used
                .role(Role.ADMIN)
                .tenant(tenant)
                .emailVerified(true)
                .googleId(sub)
                .authProvider(AuthProvider.GOOGLE)
                .avatarUrl(picture)
                .lastLoginAt(LocalDateTime.now())
                .failedLoginAttempts(0)
                .build());

        log.info("Created new user via Google OAuth [id={}] '{}' for tenant [id={}]",
                user.getId(), user.getEmail(), tenant.getId());

        emailService.sendWelcomeEmail(user.getEmail(), givenName);

        return buildAuthResponse(user);
    }

    /** If user has 2FA enabled, issue a challenge token instead of a full JWT. */
    private AuthResponse buildAuthResponseOrChallenge(User user) {
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            String challengeToken = jwtTokenProvider.generateChallengeToken(user);
            log.info("Google OAuth: 2FA required for user [id={}] '{}'", user.getId(), user.getEmail());
            return AuthResponse.builder()
                    .twoFactorRequired(true)
                    .twoFactorMethod("TOTP")
                    .challengeToken(challengeToken)
                    .email(user.getEmail())
                    .build();
        }
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        refreshTokenService.saveRefreshToken(user.getId(), refreshToken);
        UserSession session = sessionService.createSession(
                user.getId(),
                RefreshTokenService.hashToken(refreshToken),
                null,
                "Google OAuth",
                jwtTokenProvider.getRefreshExpirationMs() / 60000
        );
        String accessToken = jwtTokenProvider.generateToken(user, session.getId());

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
}
