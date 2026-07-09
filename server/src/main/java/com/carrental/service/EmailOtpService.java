package com.carrental.service;

import com.carrental.entity.EmailOtpCode;
import com.carrental.entity.EmailOtpPurpose;
import com.carrental.entity.User;
import com.carrental.repository.EmailOtpCodeRepository;
import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private static final int OTP_LENGTH            = 6;
    private static final int EXPIRY_MINUTES        = 10;
    private static final int MAX_ATTEMPTS          = 5;
    private static final int RATE_LIMIT_WINDOW_MIN = 15;
    private static final int RATE_LIMIT_MAX_SENDS  = 5;
    private static final int RESEND_COOLDOWN_SEC   = 60;

    private static final Set<String> PASSTHROUGH_CODES = Set.of(
            "EMAIL_AUTH_FAILED", "EMAIL_PROVIDER_UNREACHABLE",
            "EMAIL_TLS_FAILED", "EMAIL_PROVIDER_TIMEOUT", "EMAIL_SEND_FAILED");

    private final EmailOtpCodeRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SmtpMailService smtpMailService;
    private final PasswordEncoder passwordEncoder;

    // ── SMTP check ────────────────────────────────────────────────────────────

    public boolean isSmtpConfigured() {
        return smtpMailService.isPlatformConfigured();
    }

    // ── Send OTP ──────────────────────────────────────────────────────────────

    /**
     * Generates and emails a 6-digit OTP.
     *
     * @throws IllegalStateException  if SMTP is not configured
     * @throws IllegalStateException  if rate limit exceeded
     * @throws IllegalArgumentException if email is not verified (for non-LOGIN purposes)
     */
    @Transactional
    public void sendCode(User user, EmailOtpPurpose purpose, String ipAddress, String userAgent) {
        String maskedEmail = maskEmail(user.getEmail());

        if (!isSmtpConfigured()) {
            log.info("[EMAIL_OTP_DEBUG] userId={} purpose={} emailMasked={} smtpConfigured=false",
                    user.getId(), purpose, maskedEmail);
            throw new IllegalStateException("SMTP_NOT_CONFIGURED");
        }

        // Email must be verified before enabling OTP for non-login purposes
        if (purpose != EmailOtpPurpose.LOGIN_2FA
                && !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("EMAIL_NOT_VERIFIED");
        }

        // Rate limit: max 5 sends per 15 minutes per user+purpose
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(RATE_LIMIT_WINDOW_MIN);
        long recentSends = otpRepository.countRecentByUserIdAndPurpose(user.getId(), purpose, windowStart);
        if (recentSends >= RATE_LIMIT_MAX_SENDS) {
            log.warn("[EMAIL_OTP_DEBUG] userId={} purpose={} emailMasked={} errorCode=EMAIL_OTP_RATE_LIMITED",
                    user.getId(), purpose, maskedEmail);
            throw new IllegalStateException("EMAIL_OTP_RATE_LIMITED");
        }

        // Resend cooldown: 60 seconds since last code
        List<EmailOtpCode> recent = otpRepository.findLatestByUserIdAndPurpose(user.getId(), purpose);
        if (!recent.isEmpty()) {
            LocalDateTime lastSent = recent.get(0).getCreatedAt();
            if (lastSent != null && lastSent.plusSeconds(RESEND_COOLDOWN_SEC).isAfter(LocalDateTime.now())) {
                throw new IllegalStateException("EMAIL_OTP_RESEND_TOO_SOON");
            }
        }

        // Invalidate any existing active codes for this user+purpose
        LocalDateTime now = LocalDateTime.now();
        otpRepository.invalidateActiveByUserIdAndPurpose(user.getId(), purpose, now);

        // Generate 6-digit code
        String rawCode = generateCode();
        String codeHash = passwordEncoder.encode(rawCode);

        EmailOtpCode otpCode = EmailOtpCode.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .purpose(purpose)
                .codeHash(codeHash)
                .expiresAt(now.plusMinutes(EXPIRY_MINUTES))
                .ipAddress(ipAddress)
                .userAgent(userAgent != null && userAgent.length() > 500
                        ? userAgent.substring(0, 500) : userAgent)
                .build();
        otpRepository.save(otpCode);

        // Send the email — if this throws, the OTP record is still persisted but
        // the code was never delivered; treat as send failure.
        try {
            emailService.sendEmailOtpCode(user.getEmail(), resolveUserName(user), rawCode, EXPIRY_MINUTES, purpose);
        } catch (Exception e) {
            String errorCode = e.getMessage();
            if (errorCode == null || !PASSTHROUGH_CODES.contains(errorCode)) {
                errorCode = "EMAIL_OTP_SEND_FAILED";
            }
            log.error("[EMAIL_OTP_DEBUG] userId={} purpose={} emailMasked={} sendStatus=FAILED errorCode={}",
                    user.getId(), purpose, maskedEmail, errorCode);
            throw new IllegalStateException(errorCode);
        }

        log.info("[EMAIL_OTP_DEBUG] userId={} purpose={} emailMasked={} smtpConfigured=true codeCreated=true sendStatus=OK",
                user.getId(), purpose, maskedEmail);
    }

    // ── Verify OTP ────────────────────────────────────────────────────────────

    /**
     * Verifies a submitted code. Increments attempts on failure.
     *
     * @return true if correct and active
     * @throws IllegalStateException with a specific errorCode message on failure
     */
    @Transactional
    public boolean verifyCode(Long userId, EmailOtpPurpose purpose, String rawCode) {
        Optional<EmailOtpCode> optCode = otpRepository
                .findActiveByUserIdAndPurpose(userId, purpose, LocalDateTime.now());

        if (optCode.isEmpty()) {
            // Could be expired, exhausted, or never sent
            List<EmailOtpCode> latest = otpRepository.findLatestByUserIdAndPurpose(userId, purpose);
            if (!latest.isEmpty()) {
                EmailOtpCode last = latest.get(0);
                if (last.isExpired()) {
                    log.info("[EMAIL_OTP_DEBUG] userId={} purpose={} verifyStatus=EXPIRED", userId, purpose);
                    throw new IllegalStateException("EMAIL_OTP_EXPIRED");
                }
                if (last.isExhausted()) {
                    log.info("[EMAIL_OTP_DEBUG] userId={} purpose={} verifyStatus=TOO_MANY_ATTEMPTS", userId, purpose);
                    throw new IllegalStateException("EMAIL_OTP_TOO_MANY_ATTEMPTS");
                }
                if (last.isUsed()) {
                    log.info("[EMAIL_OTP_DEBUG] userId={} purpose={} verifyStatus=ALREADY_USED", userId, purpose);
                    throw new IllegalStateException("EMAIL_OTP_EXPIRED");
                }
            }
            log.info("[EMAIL_OTP_DEBUG] userId={} purpose={} verifyStatus=NOT_FOUND", userId, purpose);
            throw new IllegalStateException("EMAIL_OTP_EXPIRED");
        }

        EmailOtpCode otpCode = optCode.get();

        if (!passwordEncoder.matches(rawCode, otpCode.getCodeHash())) {
            otpCode.setAttempts(otpCode.getAttempts() + 1);
            otpRepository.save(otpCode);
            log.info("[EMAIL_OTP_DEBUG] userId={} purpose={} verifyStatus=INVALID attempts={}",
                    userId, purpose, otpCode.getAttempts());

            if (otpCode.isExhausted()) {
                throw new IllegalStateException("EMAIL_OTP_TOO_MANY_ATTEMPTS");
            }
            throw new IllegalStateException("EMAIL_OTP_INVALID");
        }

        // Correct — mark as used
        otpCode.setUsedAt(LocalDateTime.now());
        otpRepository.save(otpCode);
        log.info("[EMAIL_OTP_DEBUG] userId={} purpose={} verifyStatus=SUCCESS attempts={}",
                userId, purpose, otpCode.getAttempts());
        return true;
    }

    // ── Enable / Disable ──────────────────────────────────────────────────────

    @Transactional
    public void enableEmailOtp(User user) {
        user.setEmailOtpEnabled(true);
        user.setLastEmailOtpEnabledAt(LocalDateTime.now());
        user.setLastSecurityChangeAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("[EMAIL_OTP_DEBUG] userId={} emailOtpEnabled=true", user.getId());
    }

    @Transactional
    public void disableEmailOtp(User user) {
        user.setEmailOtpEnabled(false);
        user.setLastSecurityChangeAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("[EMAIL_OTP_DEBUG] userId={} emailOtpEnabled=false", user.getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateCode() {
        int code = new SecureRandom().nextInt(900_000) + 100_000; // 100000-999999
        return String.valueOf(code);
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 3) return local.charAt(0) + "***@" + domain;
        return local.substring(0, 3) + "****@" + domain;
    }

    private String resolveUserName(User user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) return user.getFirstName();
        return user.getEmail().split("@")[0];
    }
}
