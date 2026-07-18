package com.carrental.service;

import com.carrental.dto.AuthResponse;
import com.carrental.entity.*;
import com.carrental.repository.PhoneOtpRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * Phone number OTP authentication service.
 * Generates and verifies one-time passwords sent via SMS.
 * <p>
 * In production, integrate with Twilio, AWS SNS, or MessageBird for real SMS delivery.
 * For now, OTPs are logged to the console.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneAuthService {

    private final PhoneOtpRepository phoneOtpRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final SessionService sessionService;
    private final EmailService emailService;

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final int OTP_LENGTH = 6;

    /**
     * Generates an OTP for the given phone number and "sends" it via SMS.
     * Rate limited: max 3 OTPs per 15 minutes per phone number.
     */
    @Transactional
    public void sendOtp(String phoneNumber) {
        // Normalize phone number
        String normalized = normalizePhoneNumber(phoneNumber);

        // Rate limit check: count recent OTPs
        long recentOtps = phoneOtpRepository.findAll().stream()
                .filter(o -> o.getPhoneNumber().equals(normalized))
                .filter(o -> o.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(15)))
                .count();

        if (recentOtps >= 3) {
            throw new IllegalArgumentException("Too many OTP requests. Please try again in 15 minutes.");
        }

        // Delete old OTPs for this number
        phoneOtpRepository.deleteByPhoneNumber(normalized);

        // Generate OTP
        String otpCode = generateOtpCode();

        PhoneOtp otp = PhoneOtp.builder()
                .phoneNumber(normalized)
                .otpCode(otpCode)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .verified(false)
                .attempts(0)
                .build();

        phoneOtpRepository.save(otp);

        // Send SMS (production: use Twilio, AWS SNS, etc.)
        sendSms(normalized, otpCode);

        log.info("OTP sent to {}: {} (expires in {} minutes)", normalized, otpCode, OTP_EXPIRY_MINUTES);
    }

    /**
     * Verifies the OTP and authenticates the user.
     * If the user doesn't exist, creates a new tenant + user automatically.
     */
    @Transactional
    public AuthResponse verifyOtp(String phoneNumber, String otpCode) {
        String normalized = normalizePhoneNumber(phoneNumber);

        PhoneOtp otp = phoneOtpRepository.findByPhoneNumberAndOtpCode(normalized, otpCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OTP code"));

        if (otp.isExpired()) {
            throw new IllegalArgumentException("OTP has expired. Please request a new one.");
        }

        if (Boolean.TRUE.equals(otp.getVerified())) {
            throw new IllegalArgumentException("OTP has already been used.");
        }

        if (otp.getAttempts() != null && otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Too many failed attempts. Please request a new OTP.");
        }

        // Mark OTP as verified
        otp.setVerified(true);
        phoneOtpRepository.save(otp);

        // Find or create user
        Optional<User> existingUser = userRepository.findByPhoneNumber(normalized);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            log.info("Phone login for existing user [id={}] '{}'", user.getId(), user.getEmail());
        } else {
            // Create new tenant + user for phone signup
            user = createUserFromPhone(normalized);
        }

        return buildAuthResponse(user);
    }

    @Transactional
    protected User createUserFromPhone(String phoneNumber) {
        String tenantName = "Agency " + phoneNumber;
        LocalDate today = LocalDate.now();
        // Same one-calendar-month trial as the email signup flow (AuthService) — trial
        // fields/status must be set explicitly here too, otherwise this tenant never
        // registers as "in trial" even though @PrePersist defaults status to TRIAL.
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(tenantName)
                .email("phone-" + phoneNumber + "@placeholder.com")
                .subscriptionActive(true)
                .trialStartDate(today)
                .trialEndDate(today.plusMonths(Tenant.TRIAL_PERIOD_MONTHS))
                .planName("Trial")
                .status("TRIAL")
                .build());

        User user = userRepository.save(User.builder()
                .email("phone-" + phoneNumber + "@placeholder.com")
                .password("PHONE_AUTH_" + System.currentTimeMillis())
                .role(Role.ADMIN)
                .tenant(tenant)
                .phoneNumber(phoneNumber)
                .emailVerified(false)
                .failedLoginAttempts(0)
                .build());

        log.info("Created new user via phone [id={}] '{}' for tenant [id={}]",
                user.getId(), user.getEmail(), tenant.getId());

        return user;
    }

    /**
     * Mock SMS sender. In production, replace with real SMS gateway.
     */
    private void sendSms(String phoneNumber, String otpCode) {
        // TODO: Replace with Twilio / AWS SNS / MessageBird integration
        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║                 SMS SENT TO {}                 ║", phoneNumber);
        log.info("║                                                      ║");
        log.info("║  Your RentCar verification code is: {}          ║", otpCode);
        log.info("║  Expires in {} minutes.                              ║", OTP_EXPIRY_MINUTES);
        log.info("╚══════════════════════════════════════════════════════╝");
    }

    private String generateOtpCode() {
        Random random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        // Remove all non-digit characters except leading +
        String digits = phoneNumber.replaceAll("[^0-9+]", "");
        if (!digits.startsWith("+")) {
            // Default to Morocco if no country code (customize per region)
            digits = "+212" + digits.replaceFirst("^0", "");
        }
        return digits;
    }

    private AuthResponse buildAuthResponse(User user) {
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        refreshTokenService.saveRefreshToken(user.getId(), refreshToken);
        UserSession session = sessionService.createSession(
                user.getId(),
                RefreshTokenService.hashToken(refreshToken),
                null,
                "Phone OTP",
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
}
