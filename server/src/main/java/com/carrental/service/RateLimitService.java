package com.carrental.service;

import com.carrental.entity.LoginAttempt;
import com.carrental.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Rate limiting and account lockout service.
 * Tracks failed login attempts per email and IP address.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final LoginAttemptRepository loginAttemptRepository;

    @Value("${app.rate-limit.login.max-attempts:5}")
    private int maxLoginAttempts;

    @Value("${app.rate-limit.login.window-minutes:15}")
    private int loginWindowMinutes;

    @Value("${app.rate-limit.login.lockout-minutes:30}")
    private int lockoutMinutes;

    @Value("${app.rate-limit.password-reset.max-attempts:3}")
    private int maxPasswordResetAttempts;

    @Value("${app.rate-limit.password-reset.window-minutes:60}")
    private int passwordResetWindowMinutes;

    /**
     * Records a login attempt (successful or failed).
     */
    public void recordAttempt(String email, String ipAddress, boolean successful, String userAgent) {
        recordAttempt(email, ipAddress, successful, userAgent, null, null, null);
    }

    public void recordAttempt(String email, String ipAddress, boolean successful, String userAgent,
                              Long userId, Long tenantId, String failureReason) {
        LoginAttempt attempt = LoginAttempt.builder()
                .email(email)
                .ipAddress(ipAddress)
                .successful(successful)
                .userAgent(userAgent)
                .userId(userId)
                .tenantId(tenantId)
                .failureReason(failureReason)
                .suspicious(!successful && isIpRateLimited(ipAddress))
                .build();
        try {
            loginAttemptRepository.save(attempt);
        } catch (Exception e) {
            log.warn("Failed to record login attempt for email {}: {}", email, e.getMessage());
        }
    }

    /**
     * Checks if the email is rate-limited due to too many failed login attempts.
     */
    public boolean isLoginRateLimited(String email) {
        try {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(loginWindowMinutes);
            long failedAttempts = loginAttemptRepository.countByEmailAndSuccessfulFalseAndAttemptedAtAfter(email, windowStart);
            return failedAttempts >= maxLoginAttempts;
        } catch (Exception e) {
            log.warn("Failed to evaluate email login rate limit for {}: {}", email, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the IP is rate-limited due to too many failed login attempts.
     */
    public boolean isIpRateLimited(String ipAddress) {
        try {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(loginWindowMinutes);
            long failedAttempts = loginAttemptRepository.countByIpAddressAndSuccessfulFalseAndAttemptedAtAfter(ipAddress, windowStart);
            return failedAttempts >= maxLoginAttempts * 2; // IP gets more leeway
        } catch (Exception e) {
            log.warn("Failed to evaluate IP login rate limit for {}: {}", ipAddress, e.getMessage());
            return false;
        }
    }

    /**
     * Gets the number of remaining login attempts for a user.
     */
    public int getRemainingAttempts(String email) {
        try {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(loginWindowMinutes);
            long failedAttempts = loginAttemptRepository.countByEmailAndSuccessfulFalseAndAttemptedAtAfter(email, windowStart);
            return Math.max(0, (int) (maxLoginAttempts - failedAttempts));
        } catch (Exception e) {
            log.warn("Failed to read remaining login attempts for {}: {}", email, e.getMessage());
            return maxLoginAttempts;
        }
    }

    /**
     * Checks if password reset is rate-limited for an email.
     */
    public boolean isPasswordResetRateLimited(String email) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(passwordResetWindowMinutes);
        long attempts = loginAttemptRepository.countByEmailAndSuccessfulFalseAndAttemptedAtAfter(email, windowStart);
        return attempts >= maxPasswordResetAttempts;
    }

    /**
     * Cleans up old login attempt records (older than 24 hours).
     */
    public void cleanupOldAttempts() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        loginAttemptRepository.deleteByAttemptedAtBefore(cutoff);
        log.debug("Cleaned up login attempts older than 24 hours");
    }
}
