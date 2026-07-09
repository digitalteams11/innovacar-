package com.carrental.controller;

import com.carrental.entity.LoginAttempt;
import com.carrental.entity.TrustedDevice;
import com.carrental.entity.User;
import com.carrental.repository.LoginAttemptRepository;
import com.carrental.repository.TrustedDeviceRepository;
import com.carrental.security.JwtTokenProvider;
import com.carrental.service.DeviceSecurityService;
import com.carrental.service.RefreshTokenService;
import com.carrental.service.SessionService;
import com.carrental.service.TwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security-center")
@RequiredArgsConstructor
public class SecurityCenterController {
    private final TrustedDeviceRepository deviceRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final SessionService sessionService;
    private final TwoFactorService twoFactorService;
    private final JwtTokenProvider jwtTokenProvider;
    private final DeviceSecurityService deviceSecurityService;

    // ── Devices ──────────────────────────────────────────────────────────────

    @GetMapping("/devices")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> devices(HttpServletRequest request) {
        User user = currentUser();
        String currentFingerprintHash = resolveCurrentFingerprintHash(request);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        return deviceRepository.findByUserIdOrderByLastSeenAtDesc(user.getId())
                .stream()
                .filter(d -> !Boolean.TRUE.equals(d.getBlocked()))
                .filter(d -> d.getLastSeenAt() != null && d.getLastSeenAt().isAfter(cutoff))
                .map(d -> deviceMap(d, currentFingerprintHash))
                .toList();
    }

    @PatchMapping("/devices/{id}/trust")
    @Transactional
    public ResponseEntity<Map<String, Object>> trustDevice(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> request) {
        TrustedDevice device = ownedDevice(id);
        boolean trust = Boolean.TRUE.equals(request.get("trusted"));
        LocalDateTime now = LocalDateTime.now();
        if (trust) {
            device.setTrusted(true);
            device.setTrustedAt(now);
            device.setExpiresAt(now.plusDays(30));
            device.setRevokedAt(null);
        } else {
            device.setTrusted(false);
            device.setRevokedAt(now);
            device.setExpiresAt(null);
        }
        deviceRepository.save(device);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", trust ? "Device trusted for 30 days" : "Device trust removed"
        ));
    }

    @PatchMapping("/devices/{id}/block")
    @Transactional
    public ResponseEntity<Map<String, Object>> blockDevice(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> request) {
        TrustedDevice device = ownedDevice(id);
        boolean blocked = Boolean.TRUE.equals(request.get("blocked"));
        device.setBlocked(blocked);
        if (blocked) {
            device.setTrusted(false);
            sessionService.revokeAllUserSessions(currentUser().getId());
        }
        deviceRepository.save(device);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", blocked ? "Device blocked and active sessions revoked" : "Device unblocked"
        ));
    }

    @DeleteMapping("/devices/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeDevice(@PathVariable Long id) {
        deviceRepository.delete(ownedDevice(id));
        return ResponseEntity.ok(Map.of("success", true, "message", "Device removed successfully"));
    }

    // ── Login history ────────────────────────────────────────────────────────

    @GetMapping("/login-history")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> loginHistory() {
        return loginAttemptRepository.findByUserIdOrderByAttemptedAtDesc(currentUser().getId())
                .stream().limit(100).map(this::loginMap).toList();
    }

    // ── Session management ───────────────────────────────────────────────────

    @PostMapping("/logout-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> logoutAllDevices() {
        sessionService.revokeAllUserSessions(currentUser().getId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out from all devices successfully"));
    }

    @PostMapping("/logout-others")
    @Transactional
    public ResponseEntity<Map<String, Object>> logoutOtherDevices(HttpServletRequest request) {
        User user = currentUser();
        Long currentSessionId = resolveCurrentSessionId(request);
        if (currentSessionId != null) {
            sessionService.revokeAllUserSessionsExcept(user.getId(), currentSessionId);
        } else {
            sessionService.revokeAllUserSessions(user.getId());
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Signed out from all other devices successfully"));
    }

    // ── 2FA status ───────────────────────────────────────────────────────────

    @GetMapping("/2fa")
    public ResponseEntity<Map<String, Object>> twoFactorStatus() {
        User user = currentUser();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", Boolean.TRUE.equals(user.getTwoFactorEnabled()));
        data.put("method", user.getTwoFactorMethod() == null
                ? "AUTHENTICATOR" : user.getTwoFactorMethod().name());
        data.put("confirmedAt", user.getTwoFactorConfirmedAt());
        data.put("hasRecoveryCodes", user.getTwoFactorRecoveryCodesHash() != null
                && !user.getTwoFactorRecoveryCodesHash().isBlank());
        data.put("emailOtpEnabled", Boolean.TRUE.equals(user.getEmailOtpEnabled()));
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    // ── 2FA setup ────────────────────────────────────────────────────────────

    @PostMapping("/2fa/authenticator/setup")
    public Map<String, Object> setupAuthenticator() {
        User user = currentUser();
        String secret = twoFactorService.generateSecret();
        return Map.of(
                "secret", secret,
                "provisioningUri", twoFactorService.provisioningUri(user, secret)
        );
    }

    @PostMapping("/2fa/authenticator/enable")
    @Transactional
    public ResponseEntity<Map<String, Object>> enableAuthenticator(
            @RequestBody Map<String, String> request) {
        String secret = request.get("secret");
        String code   = request.get("code");
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Authenticator secret is required.");
        }
        List<String> recoveryCodes = twoFactorService.enableAuthenticator(currentUser(), secret, code);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recoveryCodes", recoveryCodes);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Two-factor authentication enabled successfully.",
                "data", data
        ));
    }

    // ── 2FA disable ──────────────────────────────────────────────────────────

    @PostMapping("/2fa/disable")
    @Transactional
    public ResponseEntity<Map<String, Object>> disableTwoFactor(
            @RequestBody Map<String, String> request) {
        String password = request.get("password");
        String code     = request.get("code");
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Current password is required to disable two-factor authentication.");
        }
        User user = currentUser();
        twoFactorService.disable(user, password, code);
        deviceSecurityService.revokeAllTrustedDevices(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Two-factor authentication disabled."));
    }

    // ── Recovery codes regeneration ──────────────────────────────────────────

    @PostMapping("/2fa/recovery-codes/regenerate")
    @Transactional
    public ResponseEntity<Map<String, Object>> regenerateRecoveryCodes(
            @RequestBody Map<String, String> request) {
        String password = request.get("password");
        String code     = request.get("code");
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Current password is required.");
        }
        List<String> newCodes = twoFactorService.regenerateRecoveryCodes(currentUser(), password, code);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recoveryCodes", newCodes);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Recovery codes regenerated successfully.",
                "data", data
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TrustedDevice ownedDevice(Long id) {
        User user = currentUser();
        return deviceRepository.findById(id)
                .filter(device -> device.getUserId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
    }

    private Map<String, Object> deviceMap(TrustedDevice device, String currentFingerprintHash) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id",              device.getId());
        value.put("name",            device.getDeviceName());
        value.put("browser",         device.getBrowser());
        value.put("operatingSystem", device.getOperatingSystem());
        value.put("ipAddress",       device.getLastIpAddress());
        value.put("trusted",         device.isActiveTrust());
        value.put("blocked",         device.getBlocked());
        value.put("current",         currentFingerprintHash != null
                && currentFingerprintHash.equals(device.getFingerprintHash()));
        value.put("createdAt",   device.getCreatedAt());
        value.put("lastSeenAt",  device.getLastSeenAt());
        value.put("trustedAt",   device.getTrustedAt());
        value.put("expiresAt",   device.getExpiresAt());
        value.put("revokedAt",   device.getRevokedAt());
        value.put("lastUsedAt",  device.getLastUsedAt());
        return value;
    }

    private Map<String, Object> loginMap(LoginAttempt attempt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id",            attempt.getId());
        value.put("email",         attempt.getEmail());
        value.put("ipAddress",     attempt.getIpAddress());
        value.put("success",       attempt.getSuccessful());
        value.put("suspicious",    attempt.getSuspicious());
        value.put("failureReason", attempt.getFailureReason());
        value.put("userAgent",     attempt.getUserAgent());
        value.put("createdAt",     attempt.getAttemptedAt());
        return value;
    }

    private String resolveCurrentFingerprintHash(HttpServletRequest request) {
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank()) return null;
        try {
            return RefreshTokenService.hashToken(deviceId);
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveCurrentSessionId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        try {
            return jwtTokenProvider.getSessionId(header.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user;
        throw new IllegalStateException("Authenticated user not found");
    }
}
