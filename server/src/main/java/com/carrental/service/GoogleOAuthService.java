package com.carrental.service;

import com.carrental.dto.AuthResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Google OAuth 2.0 authentication service.
 * Verifies Google ID tokens and creates/links user accounts.
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
    private final TwoFactorService twoFactorService;

    @Value("${app.google.client-id:}")
    private String googleClientId;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://oauth2.googleapis.com")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();

    /**
     * Authenticates a user via Google OAuth ID token.
     * If the user doesn't exist, creates a new tenant + admin account.
     */
    public AuthResponse authenticate(String idToken) {
        if (!StringUtils.hasText(googleClientId)) {
            // Fail closed, not open — verifyGoogleToken() below only enforces the
            // audience check when googleClientId is non-blank, which previously
            // meant an unconfigured client-id silently accepted a valid ID token
            // issued to ANY Google OAuth client, not just this app's.
            throw new GoogleAuthException("Google sign-in is not configured on this server.", "GOOGLE_AUTH_NOT_CONFIGURED");
        }

        GoogleUserInfo userInfo = verifyGoogleToken(idToken);
        if (userInfo == null || userInfo.email == null) {
            throw new GoogleAuthException("Your Google sign-in could not be verified. Please try again.", "GOOGLE_TOKEN_INVALID");
        }
        if (!userInfo.emailVerified) {
            throw new GoogleAuthException("Your Google account's email address is not verified.", "GOOGLE_EMAIL_NOT_VERIFIED");
        }

        String normalizedEmail = userInfo.email.trim().toLowerCase(java.util.Locale.ROOT);

        // Check if user already exists by Google ID
        Optional<User> existingByGoogle = userRepository.findByGoogleId(userInfo.sub);
        if (existingByGoogle.isPresent()) {
            return authenticateExistingUser(existingByGoogle.get());
        }

        // Check if user exists by email (link Google account) — never creates a
        // duplicate account for an email that already has one.
        Optional<User> existingByEmail = userRepository.findByEmail(normalizedEmail);
        if (existingByEmail.isPresent()) {
            User user = existingByEmail.get();
            user.setGoogleId(userInfo.sub);
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Linked Google account to existing user: {}", user.getEmail());
            return authenticateExistingUser(user);
        }

        // Create new tenant + admin user for Google signup
        return createUserFromGoogle(userInfo, normalizedEmail);
    }

    /** Rejects blocked/suspended/locked accounts before ever issuing a token — the
     *  password-login path enforces these through Spring Security's AuthenticationManager;
     *  Google OAuth bypasses that manager entirely, so it must check explicitly instead. */
    private AuthResponse authenticateExistingUser(User user) {
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
        return buildAuthResponseOrChallenge(user);
    }

    private AuthResponse createUserFromGoogle(GoogleUserInfo info, String normalizedEmail) {
        // Create tenant
        String tenantName = info.name != null ? info.name + "'s Agency" : "New Agency";
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(tenantName)
                .email(normalizedEmail)
                .subscriptionActive(true)
                .subscriptionEndDate(LocalDate.now().plusYears(1))
                .build());

        // Create user
        User user = userRepository.save(User.builder()
                .email(normalizedEmail)
                .password("GOOGLE_OAUTH_" + System.currentTimeMillis()) // Random placeholder — not used
                .role(Role.ADMIN)
                .tenant(tenant)
                .emailVerified(true)
                .googleId(info.sub)
                .failedLoginAttempts(0)
                .build());

        log.info("Created new user via Google OAuth [id={}] '{}' for tenant [id={}]",
                user.getId(), user.getEmail(), tenant.getId());

        emailService.sendWelcomeEmail(user.getEmail(), info.givenName);

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

    /**
     * Verifies a Google ID token by calling Google's tokeninfo endpoint.
     * In production, consider using the Google API Client Library for verification.
     */
    private GoogleUserInfo verifyGoogleToken(String idToken) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/tokeninfo?id_token={token}", idToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return null;

            // Validate audience — authenticate() already guarantees googleClientId
            // is non-blank before this method is ever called, so this always
            // actually enforces (never silently skipped).
            String aud = (String) response.get("aud");
            if (!googleClientId.equals(aud)) {
                log.warn("Google token audience mismatch: expected {}, got {}", googleClientId, aud);
                return null;
            }

            GoogleUserInfo info = new GoogleUserInfo();
            info.sub = (String) response.get("sub");
            info.email = (String) response.get("email");
            info.name = (String) response.get("name");
            info.givenName = (String) response.get("given_name");
            info.familyName = (String) response.get("family_name");
            info.picture = (String) response.get("picture");
            info.emailVerified = Boolean.TRUE.equals(response.get("email_verified"));

            return info;
        } catch (Exception e) {
            log.error("Failed to verify Google token: {}", e.getMessage());
            return null;
        }
    }

    // ── Inner DTO ───────────────────────────────────────────────────────────

    private static class GoogleUserInfo {
        String sub;
        String email;
        String name;
        String givenName;
        String familyName;
        String picture;
        boolean emailVerified;
    }
}
