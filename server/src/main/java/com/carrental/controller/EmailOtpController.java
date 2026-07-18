package com.carrental.controller;

import com.carrental.dto.ApiResponse;
import com.carrental.entity.EmailOtpPurpose;
import com.carrental.entity.User;
import com.carrental.repository.UserRepository;
import com.carrental.service.EmailOtpService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Email OTP management endpoints — all require an authenticated user.
 *
 * POST /api/security/email-otp/send-enable-code  – sends OTP to enable Email OTP
 * POST /api/security/email-otp/enable            – verifies OTP and enables Email OTP
 * POST /api/security/email-otp/disable           – disables Email OTP (requires password)
 * GET  /api/security/email-otp/status            – returns current Email OTP status
 */
@RestController
@RequestMapping("/api/security/email-otp")
@RequiredArgsConstructor
public class EmailOtpController {

    private final EmailOtpService  emailOtpService;
    private final UserRepository   userRepository;
    private final PasswordEncoder  passwordEncoder;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) return unauthenticated();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emailOtpEnabled",  Boolean.TRUE.equals(currentUser.getEmailOtpEnabled()));
        data.put("smtpConfigured",   emailOtpService.isSmtpConfigured());
        data.put("emailVerified",    Boolean.TRUE.equals(currentUser.getEmailVerified()));
        data.put("maskedEmail",      emailOtpService.maskEmail(currentUser.getEmail()));
        return ResponseEntity.ok(ApiResponse.success(data, "Email OTP status loaded."));
    }

    @PostMapping("/send-enable-code")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendEnableCode(
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {
        if (currentUser == null) return unauthenticated();
        String ip = getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        try {
            emailOtpService.sendCode(currentUser, EmailOtpPurpose.ENABLE_EMAIL_OTP, ip, ua);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("maskedEmail",      emailOtpService.maskEmail(currentUser.getEmail()));
            data.put("expiresInMinutes", 10);
            return ResponseEntity.ok(ApiResponse.success(data, "Verification code sent to your email."));
        } catch (IllegalStateException e) {
            return errorResponse(e.getMessage());
        } catch (IllegalArgumentException e) {
            return errorResponse(e.getMessage());
        }
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enable(
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, String> body) {
        if (currentUser == null) return unauthenticated();
        String code = body == null ? null : body.get("code");
        if (code == null || code.isBlank()) {
            return badRequest("Verification code is required.");
        }
        try {
            emailOtpService.verifyCode(currentUser.getId(), EmailOtpPurpose.ENABLE_EMAIL_OTP, code);
            emailOtpService.enableEmailOtp(currentUser);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("emailOtpEnabled", true);
            return ResponseEntity.ok(ApiResponse.success(data, "Email OTP verification enabled successfully."));
        } catch (IllegalStateException e) {
            String msg = switch (e.getMessage()) {
                case "EMAIL_OTP_EXPIRED"           -> "The code has expired. Please request a new one.";
                case "EMAIL_OTP_TOO_MANY_ATTEMPTS" -> "Too many incorrect attempts. Please request a new code.";
                case "EMAIL_OTP_INVALID"           -> "Invalid code. Please try again.";
                default                            -> e.getMessage();
            };
            return badRequest(msg);
        }
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disable(
            @AuthenticationPrincipal User currentUser,
            @RequestBody Map<String, String> body) {
        if (currentUser == null) return unauthenticated();
        String password = body == null ? null : body.get("password");
        if (password == null || password.isBlank()) {
            return badRequest("Current password is required.");
        }
        // Re-load user from DB to ensure fresh state with latest password hash
        User freshUser = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        if (!passwordEncoder.matches(password, freshUser.getPassword())) {
            return badRequest("Incorrect password.");
        }
        emailOtpService.disableEmailOtp(freshUser);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emailOtpEnabled", false);
        return ResponseEntity.ok(ApiResponse.success(data, "Email OTP verification disabled."));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private <T> ResponseEntity<ApiResponse<T>> errorResponse(String errorCode) {
        String msg = switch (errorCode) {
            case "SMTP_NOT_CONFIGURED"       -> "Email sending is not configured. Contact your platform administrator.";
            case "EMAIL_NOT_VERIFIED"        -> "Your email address must be verified before enabling Email OTP.";
            case "EMAIL_OTP_RATE_LIMITED"    -> "Too many codes sent. Please wait before requesting a new code.";
            case "EMAIL_OTP_RESEND_TOO_SOON" -> "Please wait 60 seconds before requesting a new code.";
            case "EMAIL_OTP_SEND_FAILED"     -> "Failed to send the verification code. Please try again later.";
            case "EMAIL_API_AUTH_FAILED", "EMAIL_API_UNAUTHORIZED"
                                              -> "Email provider authentication failed. Contact your administrator.";
            case "EMAIL_API_RATE_LIMITED"    -> "The email provider is rate-limiting requests. Please wait a moment and try again.";
            case "EMAIL_SENDER_NOT_VERIFIED" -> "The sending address is not verified with the email provider. Contact your administrator.";
            case "EMAIL_API_INVALID_PAYLOAD" -> "The email provider rejected this request. Please try again later.";
            case "EMAIL_API_PROVIDER_ERROR", "EMAIL_API_PROVIDER_UNAVAILABLE", "EMAIL_API_REQUEST_REJECTED"
                                              -> "The email provider could not process this request. Please try again later.";
            case "EMAIL_API_TIMEOUT"         -> "The email provider timed out. Please try again later.";
            case "EMAIL_API_ENDPOINT_INVALID"-> "The email provider endpoint could not be reached. Contact your administrator.";
            case "EMAIL_API_NETWORK_ERROR"   -> "A network error prevented the code from being sent. Please try again later.";
            case "EMAIL_CONFIGURATION_MISSING" -> "Email sending is not fully configured. Contact your administrator.";
            case "EMAIL_SEND_FAILED"         -> "Failed to send the verification code. Please try again later.";
            default                          -> "Failed to send the verification code. Please try again later.";
        };
        return ResponseEntity.badRequest()
                .body(ApiResponse.<T>builder()
                        .success(false).message(msg)
                        .severity("warning").timestamp(java.time.Instant.now().toString()).build());
    }

    private static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.<T>builder()
                        .success(false).message(message)
                        .severity("error").timestamp(java.time.Instant.now().toString()).build());
    }

    private static <T> ResponseEntity<ApiResponse<T>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<T>builder()
                        .success(false).message("Authentication required.")
                        .severity("error").timestamp(java.time.Instant.now().toString()).build());
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri;
        return request.getRemoteAddr();
    }
}
