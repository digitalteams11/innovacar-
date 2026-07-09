package com.carrental.service;

import com.carrental.entity.TrustedDevice;
import com.carrental.entity.User;
import com.carrental.repository.TrustedDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSecurityService {

    private static final int TRUST_DURATION_DAYS = 30;

    private final TrustedDeviceRepository deviceRepository;

    // ── Login recording ──────────────────────────────────────────────────────

    @Transactional
    public TrustedDevice recordLogin(User user, String fingerprint, String deviceName,
                                     String ipAddress, String userAgent) {
        String hash = RefreshTokenService.hashToken(
                fingerprint == null || fingerprint.isBlank() ? userAgent : fingerprint);
        TrustedDevice device = deviceRepository.findByUserIdAndFingerprintHash(user.getId(), hash)
                .orElseGet(() -> TrustedDevice.builder()
                        .userId(user.getId())
                        .tenantId(user.getTenant() != null ? user.getTenant().getId() : null)
                        .fingerprintHash(hash)
                        .trusted(false)
                        .blocked(false)
                        .build());
        device.setDeviceName(clean(deviceName, detectDevice(userAgent)));
        device.setBrowser(detectBrowser(userAgent));
        device.setOperatingSystem(detectOperatingSystem(userAgent));
        device.setLastIpAddress(ipAddress);
        device.setLastSeenAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(device.getBlocked())) {
            throw new LockedException("This device has been blocked. Contact your administrator.");
        }
        return deviceRepository.save(device);
    }

    // ── Trust management ─────────────────────────────────────────────────────

    /**
     * Returns true when the device fingerprint maps to an active (trusted, not revoked,
     * not expired) record for the given user.
     */
    @Transactional(readOnly = true)
    public boolean isDeviceTrusted(Long userId, String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return false;
        String hash = RefreshTokenService.hashToken(fingerprint);
        return deviceRepository.findActiveTrusted(userId, hash, LocalDateTime.now()).isPresent();
    }

    /**
     * Looks up the device record (if any) matching this fingerprint, for
     * [TRUSTED_DEVICE_DEBUG] logging — never exposes the raw fingerprint itself.
     */
    @Transactional(readOnly = true)
    public Optional<com.carrental.entity.TrustedDevice> findByFingerprint(Long userId, String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return Optional.empty();
        String hash = RefreshTokenService.hashToken(fingerprint);
        return deviceRepository.findByUserIdAndFingerprintHash(userId, hash);
    }

    /**
     * Marks a device as explicitly trusted for {@value TRUST_DURATION_DAYS} days.
     * Creates the device record if it doesn't exist yet.
     */
    @Transactional
    public void trustDevice(User user, String fingerprint, String deviceName,
                            String ipAddress, String userAgent) {
        if (fingerprint == null || fingerprint.isBlank()) {
            log.warn("[TRUST_DEVICE] fingerprint is blank for userId={}, skipping", user.getId());
            return;
        }
        String hash = RefreshTokenService.hashToken(fingerprint);
        TrustedDevice device = deviceRepository.findByUserIdAndFingerprintHash(user.getId(), hash)
                .orElseGet(() -> TrustedDevice.builder()
                        .userId(user.getId())
                        .tenantId(user.getTenant() != null ? user.getTenant().getId() : null)
                        .fingerprintHash(hash)
                        .blocked(false)
                        .build());
        LocalDateTime now = LocalDateTime.now();
        device.setTrusted(true);
        device.setTrustedAt(now);
        device.setExpiresAt(now.plusDays(TRUST_DURATION_DAYS));
        device.setRevokedAt(null);
        device.setLastUsedAt(now);
        device.setDeviceName(clean(deviceName, detectDevice(userAgent)));
        device.setBrowser(detectBrowser(userAgent));
        device.setOperatingSystem(detectOperatingSystem(userAgent));
        device.setLastIpAddress(ipAddress);
        device.setLastSeenAt(now);
        deviceRepository.save(device);
        log.info("[TRUST_DEVICE] userId={} trusted for {} days", user.getId(), TRUST_DURATION_DAYS);
    }

    /**
     * Revokes all trusted device records for the user.
     * Must be called on password change, 2FA disable, and logout-all.
     */
    @Transactional
    public void revokeAllTrustedDevices(Long userId) {
        int count = deviceRepository.revokeAllByUserId(userId, LocalDateTime.now());
        if (count > 0) {
            log.info("[TRUST_REVOKE] Revoked {} trusted device(s) for userId={}", count, userId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String detectBrowser(String agent) {
        String value = normalized(agent);
        if (value.contains("edg/")) return "Microsoft Edge";
        if (value.contains("chrome/")) return "Chrome";
        if (value.contains("firefox/")) return "Firefox";
        if (value.contains("safari/")) return "Safari";
        return "Unknown browser";
    }

    private String detectOperatingSystem(String agent) {
        String value = normalized(agent);
        if (value.contains("windows")) return "Windows";
        if (value.contains("android")) return "Android";
        if (value.contains("iphone") || value.contains("ipad")) return "iOS";
        if (value.contains("mac os")) return "macOS";
        if (value.contains("linux")) return "Linux";
        return "Unknown OS";
    }

    private String detectDevice(String agent) {
        String value = normalized(agent);
        if (value.contains("iphone")) return "iPhone";
        if (value.contains("ipad")) return "iPad";
        if (value.contains("android")) return value.contains("mobile") ? "Android phone" : "Android tablet";
        return "Computer";
    }

    private String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String clean(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().substring(0, Math.min(value.trim().length(), 150));
    }
}
