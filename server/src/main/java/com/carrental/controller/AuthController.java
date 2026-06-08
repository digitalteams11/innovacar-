package com.carrental.controller;

import com.carrental.dto.*;
import com.carrental.service.AuthService;
import com.carrental.service.GoogleOAuthService;
import com.carrental.service.PhoneAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final PhoneAuthService   phoneAuthService;

    // ── Registration & Signup ────────────────────────────────────────────────

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    // ── Token Refresh ────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
        authService.logout(refreshToken);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Logged out successfully")
                .success(true)
                .build());
    }

    // ── Google OAuth ─────────────────────────────────────────────────────────

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleAuth(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = googleOAuthService.authenticate(request.getIdToken());
        return ResponseEntity.ok(response);
    }

    // ── Phone OTP ────────────────────────────────────────────────────────────

    @PostMapping("/phone/send-otp")
    public ResponseEntity<MessageResponse> sendPhoneOtp(@Valid @RequestBody PhoneOtpRequest request) {
        phoneAuthService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok(MessageResponse.builder()
                .message("OTP sent successfully. Check your SMS.")
                .success(true)
                .build());
    }

    @PostMapping("/phone/verify-otp")
    public ResponseEntity<AuthResponse> verifyPhoneOtp(@Valid @RequestBody PhoneOtpVerifyRequest request) {
        AuthResponse response = phoneAuthService.verifyOtp(request.getPhoneNumber(), request.getOtpCode());
        return ResponseEntity.ok(response);
    }

    // ── Password Reset ───────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        // Always return success to prevent email enumeration
        return ResponseEntity.ok(MessageResponse.builder()
                .message("If an account exists with that email, a reset link has been sent")
                .success(true)
                .build());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Password reset successfully. Please log in with your new password.")
                .success(true)
                .build());
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
}
