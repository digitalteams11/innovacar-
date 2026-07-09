package com.carrental.controller;

import com.carrental.dto.*;
import com.carrental.entity.User;
import com.carrental.exception.TokenRefreshException;
import com.carrental.exception.TwoFactorVerificationException;
import com.carrental.security.AuthCookieService;
import com.carrental.service.AuthService;
import com.carrental.service.GoogleOAuthService;
import com.carrental.service.TwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints — no JWT required.
 *
 * <pre>
 * POST /api/auth/signup          – Create tenant + admin user
 * POST /api/auth/login           – Authenticate user → returns JWT pair
 * POST /api/auth/register        – Register user under existing tenant
 * POST /api/auth/refresh         – Refresh access token
 * POST /api/auth/logout          – Revoke refresh token
 * POST /api/auth/google          – Google OAuth login/signup
 * POST /api/auth/forgot-password – Request password reset email
 * POST /api/auth/reset-password  – Confirm password reset
 * POST /api/auth/verify-email    – Verify email address
 * POST /api/auth/resend-verify   – Resend verification email
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService        authService;
    private final GoogleOAuthService googleOAuthService;
    private final AuthCookieService  authCookieService;
    private final TwoFactorService   twoFactorService;

    // ── Registration & Signup ────────────────────────────────────────────────

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.signup(request);
        return authResponse(response, HttpStatus.CREATED, httpRequest, httpResponse, "Signup successful");
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.register(request);
        return authResponse(response, HttpStatus.CREATED, httpRequest, httpResponse, "Registration successful");
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authService.login(request, ipAddress, userAgent);
        return authResponse(response, HttpStatus.OK, httpRequest, httpResponse, "Login successful");
    }

    // ── 2FA second-leg verification ─────────────────────────────────────────

    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyTwoFactor(
            @RequestBody java.util.Map<String, Object> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String challengeToken    = (String) request.get("challengeToken");
        String code              = (String) request.get("code");
        String recoveryCode      = (String) request.get("recoveryCode");
        String deviceFingerprint = (String) request.get("deviceFingerprint");
        String deviceName        = (String) request.get("deviceName");
        Boolean trustDevice      = request.get("trustDevice") instanceof Boolean b ? b
                                   : Boolean.parseBoolean(String.valueOf(request.get("trustDevice")));
        String ipAddress         = getClientIp(httpRequest);
        String userAgent         = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.verifyTwoFactor(
                challengeToken, code, recoveryCode, deviceFingerprint, deviceName, trustDevice,
                ipAddress, userAgent);
        return authResponse(response, HttpStatus.OK, httpRequest, httpResponse, "Two-factor authentication successful");
    }

    // ── Email OTP 2FA second-leg ─────────────────────────────────────────────

    @PostMapping("/2fa/email/send")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> sendEmailOtp(
            @RequestBody java.util.Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String challengeToken = (String) request.get("challengeToken");
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        try {
            String maskedEmail = authService.sendLoginEmailOtp(challengeToken, ipAddress, userAgent);
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("maskedEmail", maskedEmail);
            data.put("expiresInMinutes", 10);
            return ResponseEntity.ok(ApiResponse.success(data, "Verification code sent to your email."));
        } catch (TwoFactorVerificationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<java.util.Map<String, Object>>builder()
                            .success(false).message(e.getMessage())
                            .severity("error").timestamp(java.time.Instant.now().toString()).build());
        } catch (IllegalStateException e) {
            String msg = switch (e.getMessage()) {
                case "EMAIL_OTP_RATE_LIMITED"    -> "Too many codes sent. Please wait before requesting a new code.";
                case "EMAIL_OTP_RESEND_TOO_SOON" -> "Please wait 60 seconds before requesting a new code.";
                case "EMAIL_OTP_SEND_FAILED"     -> "Failed to send the code. Please try again later.";
                default                          -> e.getMessage();
            };
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<java.util.Map<String, Object>>builder()
                            .success(false).message(msg)
                            .severity("warning").timestamp(java.time.Instant.now().toString()).build());
        }
    }

    @PostMapping("/2fa/email/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmailOtp(
            @RequestBody java.util.Map<String, Object> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String challengeToken    = (String) request.get("challengeToken");
        String code              = (String) request.get("code");
        String deviceFingerprint = (String) request.get("deviceFingerprint");
        String deviceName        = (String) request.get("deviceName");
        Boolean trustDevice      = request.get("trustDevice") instanceof Boolean b ? b
                                   : Boolean.parseBoolean(String.valueOf(request.get("trustDevice")));
        String ipAddress         = getClientIp(httpRequest);
        String userAgent         = httpRequest.getHeader("User-Agent");

        try {
            AuthResponse response = authService.verifyTwoFactorEmail(
                    challengeToken, code, deviceFingerprint, deviceName, trustDevice,
                    ipAddress, userAgent);
            return authResponse(response, HttpStatus.OK, httpRequest, httpResponse,
                    "Two-factor authentication successful");
        } catch (TwoFactorVerificationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<AuthResponse>builder()
                            .success(false).message(e.getMessage())
                            .severity("error").timestamp(java.time.Instant.now().toString()).build());
        } catch (IllegalStateException e) {
            String msg = switch (e.getMessage()) {
                case "EMAIL_OTP_EXPIRED"           -> "The code has expired. Please request a new one.";
                case "EMAIL_OTP_TOO_MANY_ATTEMPTS" -> "Too many incorrect attempts. Please request a new code.";
                case "EMAIL_OTP_INVALID"           -> "Invalid verification code. Please try again.";
                default                            -> e.getMessage();
            };
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<AuthResponse>builder()
                            .success(false).message(msg)
                            .severity("error").timestamp(java.time.Instant.now().toString()).build());
        }
    }

    // ── Token Refresh ────────────────────────────────────────────────────────

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @RequestBody java.util.Map<String, String> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.changeCurrentPassword(
                request == null ? null : request.get("currentPassword"),
                request == null ? null : request.get("newPassword"));
        return authResponse(response, HttpStatus.OK, httpRequest, httpResponse, "Password changed successfully");
    }
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        RefreshTokenRequest resolved = new RefreshTokenRequest();
        resolved.setRefreshToken(resolveRefreshToken(request, httpRequest));
        AuthResponse response = authService.refreshToken(resolved);
        return authResponse(response, HttpStatus.OK, httpRequest, httpResponse, "Token refreshed");
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshToken = optionalRefreshToken(request, httpRequest);
        authService.logout(refreshToken);
        authCookieService.clearAuthCookies(httpResponse);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Logged out successfully")
                .success(true)
                .build());
    }

    // ── Google OAuth ─────────────────────────────────────────────────────────

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleAuth(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = googleOAuthService.authenticate(request.getIdToken());
        return authResponse(response, HttpStatus.OK, httpRequest, httpResponse, "Google login successful");
    }

    // ── Phone OTP (disabled) ─────────────────────────────────────────────────
    // Phone login is intentionally disabled — no SMS/OTP provider is wired up
    // yet, and the product only supports Email/Password + Google login for now.
    // Kept as a disabled stub (rather than deleted) so the SMS provider can be
    // plugged back in later without re-adding routes/DTOs. Phone NUMBERS used
    // as contact info (user/client/agency/employee profile) are unaffected.

    @PostMapping("/phone/send-otp")
    public ResponseEntity<java.util.Map<String, Object>> sendPhoneOtp(@Valid @RequestBody PhoneOtpRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(phoneLoginDisabledBody());
    }

    @PostMapping("/phone/verify-otp")
    public ResponseEntity<java.util.Map<String, Object>> verifyPhoneOtp(@Valid @RequestBody PhoneOtpVerifyRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(phoneLoginDisabledBody());
    }

    private java.util.Map<String, Object> phoneLoginDisabledBody() {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("success", false);
        body.put("errorCode", "PHONE_LOGIN_DISABLED");
        body.put("message", "Phone login is currently disabled. Please sign in with email/password or Google.");
        body.put("data", null);
        return body;
    }

    // ── Password Reset ───────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        authService.requestPasswordReset(request, ip, ua);
        // Always return same success message — never reveal whether email exists
        return ResponseEntity.ok(ApiResponse.success(null,
                "If an account exists with that email, a reset code has been sent."));
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(
            @Valid @RequestBody VerifyResetCodeRequest request) {
        try {
            String resetSessionToken = authService.verifyResetCode(request.getEmail(), request.getCode());
            java.util.Map<String, String> data = java.util.Map.of("resetSessionToken", resetSessionToken);
            return ResponseEntity.ok(ApiResponse.success(data, "Code verified. You can now set a new password."));
        } catch (IllegalArgumentException e) {
            String code = e.getMessage();
            String userMsg = switch (code) {
                case "CODE_EXPIRED"       -> "This code has expired. Please request a new one.";
                case "TOO_MANY_ATTEMPTS"  -> "Too many incorrect attempts. Please request a new code.";
                default                   -> "Invalid or incorrect code. Please try again.";
            };
            return ResponseEntity.badRequest().body(ApiResponse.error(userMsg, "error", null));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        try {
            authService.confirmPasswordReset(request);
            return ResponseEntity.ok(ApiResponse.success(null,
                    "Password reset successfully. Please log in with your new password."));
        } catch (IllegalArgumentException e) {
            String code = e.getMessage();
            String userMsg = switch (code) {
                case "INVALID_SESSION_TOKEN" -> "Your reset session has expired. Please start over.";
                case "WEAK_PASSWORD"         -> "Password does not meet the strength requirements.";
                default                      -> e.getMessage();
            };
            return ResponseEntity.badRequest().body(ApiResponse.error(userMsg, "error", null));
        }
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    // ── Email Verification ───────────────────────────────────────────────────

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Email verified successfully")
                .success(true)
                .build());
    }

    @PostMapping("/resend-verify")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("If an unverified account exists, a verification email has been sent")
                .success(true)
                .build());
    }

    // ── Code-based email verification (authenticated) ───────────────────────

    @PostMapping("/send-email-verification-code")
    public ResponseEntity<MessageResponse> sendEmailVerificationCode(
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) return ResponseEntity.status(401)
                .body(MessageResponse.builder().message("Unauthorized").success(false).build());
        authService.sendEmailVerificationCode(currentUser);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Verification code sent to " + currentUser.getEmail())
                .success(true)
                .build());
    }

    @PostMapping("/verify-email-code")
    public ResponseEntity<?> verifyEmailCode(
            @AuthenticationPrincipal User currentUser,
            @RequestBody java.util.Map<String, String> body) {
        if (currentUser == null) return ResponseEntity.status(401)
                .body(MessageResponse.builder().message("Unauthorized").success(false).build());
        String code = body == null ? null : body.get("code");
        try {
            authService.verifyEmailCode(currentUser, code);
            return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully."));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(ApiResponse.success(null, "Email is already verified."));
        } catch (IllegalArgumentException e) {
            String userMsg = switch (e.getMessage()) {
                case "CODE_EXPIRED"       -> "This code has expired. Request a new one.";
                case "TOO_MANY_ATTEMPTS"  -> "Too many incorrect attempts. Request a new code.";
                case "CODE_NOT_FOUND"     -> "No verification code found. Request a new one.";
                default                   -> "Invalid verification code. Please try again.";
            };
            return ResponseEntity.badRequest().body(ApiResponse.error(userMsg, e.getMessage(), null));
        }
    }

    // ── Security status (authenticated) ──────────────────────────────────────

    /**
     * Returns the authenticated user's 2FA / email-verification status.
     * Accessible via a valid JWT even though /api/auth/** is permitAll —
     * the JWT filter still populates SecurityContextHolder.
     */
    @GetMapping("/security-status")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> securityStatus(
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) return unauthenticated();
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("emailVerified", Boolean.TRUE.equals(currentUser.getEmailVerified()));
        data.put("twoFactorEnabled", Boolean.TRUE.equals(currentUser.getTwoFactorEnabled()));
        data.put("twoFactorConfirmedAt", currentUser.getTwoFactorConfirmedAt() != null
                ? currentUser.getTwoFactorConfirmedAt().toString() : null);
        data.put("role", currentUser.getRole() != null ? currentUser.getRole().name() : null);
        data.put("email", currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.success(data, "Security status loaded."));
    }

    // ── 2FA management (setup / confirm / disable) ───────────────────────────

    /**
     * Initiates 2FA setup. The secret is stored server-side (encrypted) in pending
     * fields and reused for 10 minutes — refreshing the page returns the same QR.
     * Returns {@code provisioningUri} (for QR scan) and {@code manualSecret}.
     * The raw secret is NEVER sent to the frontend.
     */
    @PostMapping("/2fa/setup")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> setup2fa(
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) return unauthenticated();
        java.util.Map<String, String> setupData = twoFactorService.initSetup(currentUser);
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>(setupData);
        return ResponseEntity.ok(ApiResponse.success(data, "Two-factor authentication setup initiated."));
    }

    /**
     * Confirms the 2FA setup. Accepts only {@code { "code": "123456" }}.
     * The server reads the pending secret from the DB — the raw secret is never
     * sent by the frontend. Codes with spaces are normalized before verification.
     */
    @PostMapping("/2fa/confirm")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> confirm2fa(
            @AuthenticationPrincipal User currentUser,
            @RequestBody java.util.Map<String, String> body) {
        if (currentUser == null) return unauthenticated();
        String code = body == null ? null : body.get("code");
        if (code == null || code.isBlank()) {
            return badRequest("Verification code is required.");
        }
        // Normalize: strip spaces, e.g. "123 456" → "123456"
        String normalizedCode = code.trim().replace(" ", "");
        try {
            java.util.List<String> recoveryCodes = twoFactorService.confirmSetup(currentUser, normalizedCode);
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("recoveryCodes", recoveryCodes);
            data.put("twoFactorEnabled", true);
            return ResponseEntity.ok(ApiResponse.success(data, "Two-factor authentication enabled successfully."));
        } catch (IllegalStateException e) {
            if ("SETUP_EXPIRED".equals(e.getMessage())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<java.util.Map<String, Object>>builder()
                                .success(false)
                                .message("This setup QR code has expired. Please click 'Set up authenticator' to start again.")
                                .severity("error")
                                .timestamp(java.time.Instant.now().toString())
                                .build());
            }
            return badRequest(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("INVALID_2FA_CODE".equals(e.getMessage())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<java.util.Map<String, Object>>builder()
                                .success(false)
                                .message("Invalid authenticator code. Make sure your phone time is set to automatic and enter the newest 6-digit code.")
                                .severity("error")
                                .timestamp(java.time.Instant.now().toString())
                                .build());
            }
            return badRequest(e.getMessage());
        }
    }

    /**
     * Disables 2FA. Requires the current password AND a valid TOTP code.
     */
    @PostMapping("/2fa/disable")
    public ResponseEntity<ApiResponse<Void>> disable2fa(
            @AuthenticationPrincipal User currentUser,
            @RequestBody java.util.Map<String, String> body) {
        if (currentUser == null) return unauthenticated();
        String password = body == null ? null : body.get("password");
        String code = body == null ? null : body.get("code");
        twoFactorService.disable(currentUser, password, code);
        return ResponseEntity.ok(ApiResponse.success("Two-factor authentication disabled."));
    }

    /**
     * Regenerates recovery codes. Requires current password + valid TOTP code.
     * New codes are returned once; old codes are immediately invalidated.
     */
    @PostMapping("/2fa/regenerate-codes")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> regenerateCodes(
            @AuthenticationPrincipal User currentUser,
            @RequestBody java.util.Map<String, String> body) {
        if (currentUser == null) return unauthenticated();
        String password = body == null ? null : body.get("password");
        String code = body == null ? null : body.get("code");
        java.util.List<String> newCodes = twoFactorService.regenerateRecoveryCodes(currentUser, password, code);
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("recoveryCodes", newCodes);
        return ResponseEntity.ok(ApiResponse.success(data, "Recovery codes regenerated successfully."));
    }

    // ── Private response helpers ─────────────────────────────────────────────

    private static <T> ResponseEntity<ApiResponse<T>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<T>builder()
                        .success(false)
                        .message("Authentication required.")
                        .severity("error")
                        .timestamp(java.time.Instant.now().toString())
                        .build());
    }

    private static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.<T>builder()
                        .success(false)
                        .message(message)
                        .severity("error")
                        .timestamp(java.time.Instant.now().toString())
                        .build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String resolveRefreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        if (request != null && request.getRefreshToken() != null
                && !request.getRefreshToken().isBlank()) {
            return request.getRefreshToken();
        }
        String cookieToken = authCookieService.readRefreshToken(httpRequest);
        if (cookieToken == null || cookieToken.isBlank()) {
            throw new TokenRefreshException("Session expired. Please login again.");
        }
        return cookieToken;
    }

    private String optionalRefreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        if (request != null && request.getRefreshToken() != null
                && !request.getRefreshToken().isBlank()) {
            return request.getRefreshToken();
        }
        String cookieToken = authCookieService.readRefreshToken(httpRequest);
        return cookieToken == null || cookieToken.isBlank() ? null : cookieToken;
    }

    private ResponseEntity<ApiResponse<AuthResponse>> authResponse(
            AuthResponse response,
            HttpStatus status,
            HttpServletRequest request,
            HttpServletResponse servletResponse,
            String message) {
        // Challenge responses have no tokens — never set cookies for them
        if (!Boolean.TRUE.equals(response.getTwoFactorRequired())
                && authCookieService.usesCookieTransport(request)) {
            authCookieService.writeAuthCookies(servletResponse, response);
            response.setAccessToken(null);
            response.setRefreshToken(null);
            response.setTokenType("Cookie");
        }
        return ResponseEntity.status(status).body(ApiResponse.success(response, message));
    }
}
