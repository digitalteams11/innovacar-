package com.carrental.service;

import com.carrental.dto.AuthResponse;
import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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
        // Verify the token with Google
        GoogleUserInfo userInfo = verifyGoogleToken(idToken);

        if (userInfo == null || userInfo.email == null) {
            throw new IllegalArgumentException("Invalid Google ID token");
        }

        // Check if user already exists by Google ID
        Optional<User> existingByGoogle = userRepository.findByGoogleId(userInfo.sub);
        if (existingByGoogle.isPresent()) {
            User user = existingByGoogle.get();
            return buildAuthResponse(user);
        }

        // Check if user exists by email (link Google account)
        Optional<User> existingByEmail = userRepository.findByEmail(userInfo.email);
        if (existingByEmail.isPresent()) {
            User user = existingByEmail.get();
            user.setGoogleId(userInfo.sub);
            user.setEmailVerified(true);
            userRepository.save(user);
            log.info("Linked Google account to existing user: {}", user.getEmail());
            return buildAuthResponse(user);
        }

        // Create new tenant + admin user for Google signup
        return createUserFromGoogle(userInfo);
    }

    private AuthResponse createUserFromGoogle(GoogleUserInfo info) {
        // Create tenant
        String tenantName = info.name != null ? info.name + "'s Agency" : "New Agency";
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(tenantName)
                .email(info.email)
                .subscriptionActive(true)
                .subscriptionEndDate(LocalDate.now().plusYears(1))
                .build());

        // Create user
        User user = userRepository.save(User.builder()
                .email(info.email)
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

            // Validate audience
            String aud = (String) response.get("aud");
            if (googleClientId != null && !googleClientId.isBlank() && !googleClientId.equals(aud)) {
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
