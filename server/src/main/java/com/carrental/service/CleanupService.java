package com.carrental.service;

import com.carrental.repository.PhoneOtpRepository;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled cleanup service for removing expired tokens and sessions.
 * Runs periodically to keep the database clean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final RefreshTokenService refreshTokenService;
    private final SessionService sessionService;
    private final RateLimitService rateLimitService;
    private final PhoneOtpRepository phoneOtpRepository;

    /**
     * Clean up expired refresh tokens every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        log.info("Running scheduled cleanup: expired refresh tokens");
        refreshTokenService.cleanupExpiredTokens();
    }

    /**
     * Clean up expired sessions every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("Running scheduled cleanup: expired sessions");
        sessionService.cleanupExpiredSessions();
    }

    /**
     * Clean up old login attempts every 6 hours.
     */
    @Scheduled(fixedRate = 21600000) // 6 hours
    @Transactional
    public void cleanupOldLoginAttempts() {
        log.info("Running scheduled cleanup: old login attempts");
        rateLimitService.cleanupOldAttempts();
    }

    /**
     * Clean up expired phone OTPs every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void cleanupExpiredPhoneOtps() {
        log.info("Running scheduled cleanup: expired phone OTPs");
        phoneOtpRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
