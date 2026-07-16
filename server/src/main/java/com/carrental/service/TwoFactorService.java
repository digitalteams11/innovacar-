package com.carrental.service;

import com.carrental.entity.TwoFactorMethod;
import com.carrental.entity.User;
import com.carrental.repository.UserRepository;
import com.carrental.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int TIME_STEP_SECONDS = 30;

    // Recovery code alphabet — no ambiguous characters (0/O, 1/I/L)
    private static final String RECOVERY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RECOVERY_CODE_COUNT = 10;

    private final EncryptionUtil encryptionUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceSecurityService deviceSecurityService;

    // ── Secret generation ────────────────────────────────────────────────────

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String provisioningUri(User user, String secret) {
        // Standard otpauth URI per Google Authenticator Key Uri Format spec.
        // Issuer has no spaces so no encoding needed; email is safe in path when @/. left as-is
        // but we encode it to be strict. Spaces must be %20 not + (+ is query-string only).
        String issuer  = "RentCar";
        String account = user.getEmail();
        String label   = issuer + ":" + urlEncodeComponent(account);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + issuer
                + "&algorithm=SHA1"
                + "&digits=6"
                + "&period=30";
    }

    // ── TOTP verification ────────────────────────────────────────────────────

    public boolean verify(User user, String code) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) return true;
        if (user.getTwoFactorMethod() != TwoFactorMethod.AUTHENTICATOR
                || user.getTwoFactorSecretEncrypted() == null) {
            log.warn("[2FA_VERIFY_DEBUG] userId={} hasSecret=false — method={} result=FAILED",
                    user.getId(), user.getTwoFactorMethod());
            return false;
        }
        boolean result = verifyCode(encryptionUtil.decrypt(user.getTwoFactorSecretEncrypted()), code);
        log.debug("[2FA_VERIFY_DEBUG] userId={} email={} hasSecret=true codeLength={} " +
                "serverTime={} timeWindow=1 result={}",
                user.getId(), user.getEmail(),
                code != null ? code.trim().length() : 0,
                System.currentTimeMillis() / 1000,
                result ? "SUCCESS" : "FAILED");
        return result;
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) return false;
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{6}")) return false;
        long counter = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (constantTimeEquals(generateCode(secret, counter + offset), trimmed)) return true;
        }
        return false;
    }

    // ── Enable ───────────────────────────────────────────────────────────────

    /**
     * Validates the first TOTP code, encrypts and stores the secret, generates
     * recovery codes, and marks 2FA as confirmed.
     *
     * @return plain-text recovery codes — returned ONCE, not stored in plain text.
     */
    @Transactional
    public List<String> enableAuthenticator(User user, String secret, String code) {
        if (!verifyCode(secret, code)) {
            throw new IllegalArgumentException("Invalid authenticator code.");
        }
        List<String> recoveryCodes = generateRecoveryCodes();
        user.setTwoFactorMethod(TwoFactorMethod.AUTHENTICATOR);
        user.setTwoFactorSecretEncrypted(encryptionUtil.encrypt(secret));
        user.setTwoFactorEnabled(true);
        user.setTwoFactorConfirmedAt(LocalDateTime.now());
        user.setTwoFactorRecoveryCodesHash(hashRecoveryCodes(recoveryCodes));
        userRepository.save(user);
        log.info("[2FA_ENABLE] userId={} method=AUTHENTICATOR recoveryCodes={}", user.getId(), RECOVERY_CODE_COUNT);
        return recoveryCodes;
    }

    // ── Disable ──────────────────────────────────────────────────────────────

    /**
     * Requires the current password AND a valid TOTP code before disabling 2FA.
     */
    @Transactional
    public void disable(User user, String password, String code) {
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect password.");
        }
        if (!verify(user, code)) {
            throw new IllegalArgumentException("Invalid authenticator code.");
        }
        user.setTwoFactorEnabled(false);
        user.setTwoFactorMethod(null);
        user.setTwoFactorSecretEncrypted(null);
        user.setTwoFactorConfirmedAt(null);
        user.setTwoFactorRecoveryCodesHash(null);
        userRepository.save(user);
        // Trusted devices only make sense as a 2FA shortcut — once 2FA is off,
        // every device must go through full credential login again.
        deviceSecurityService.revokeAllTrustedDevices(user.getId());
        log.info("[2FA_DISABLE] userId={}", user.getId());
    }

    // ── Recovery codes ───────────────────────────────────────────────────────

    /**
     * Verifies a recovery code (case-insensitive) and immediately removes it
     * from the stored list so it cannot be reused.
     */
    @Transactional
    public boolean verifyAndConsumeRecoveryCode(User user, String rawCode) {
        String stored = user.getTwoFactorRecoveryCodesHash();
        if (stored == null || stored.isBlank()) return false;
        String normalized = rawCode.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        List<String> hashes = new ArrayList<>(Arrays.asList(stored.split(",")));
        for (int i = 0; i < hashes.size(); i++) {
            String candidate = hashes.get(i).trim();
            if (candidate.isBlank()) continue;
            // Normalize code against the recovery format (strip dashes) then compare
            if (passwordEncoder.matches(normalized, candidate)) {
                hashes.remove(i);
                user.setTwoFactorRecoveryCodesHash(
                        hashes.stream().filter(h -> !h.isBlank()).collect(Collectors.joining(",")));
                userRepository.save(user);
                log.info("[2FA_RECOVERY_USED] userId={} codesRemaining={}", user.getId(), hashes.size() - 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Regenerates recovery codes after verifying the user's password and current TOTP.
     *
     * @return new plain-text recovery codes.
     */
    @Transactional
    public List<String> regenerateRecoveryCodes(User user, String password, String code) {
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect password.");
        }
        if (!verify(user, code)) {
            throw new IllegalArgumentException("Invalid authenticator code.");
        }
        List<String> newCodes = generateRecoveryCodes();
        user.setTwoFactorRecoveryCodesHash(hashRecoveryCodes(newCodes));
        userRepository.save(user);
        log.info("[2FA_RECOVERY_REGEN] userId={}", user.getId());
        return newCodes;
    }

    // ── Setup initiation ─────────────────────────────────────────────────────

    /**
     * Initiates 2FA setup. Stores the TOTP secret encrypted in the PENDING fields
     * so the active secret (if any) is never disturbed. Reuses the pending secret
     * if it was generated within the last 10 minutes — this ensures a page refresh
     * does not invalidate an already-scanned QR code.
     */
    @Transactional
    public java.util.Map<String, String> initSetup(User user) {
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException(
                    "Two-factor authentication is already enabled. Disable it first before setting up a new authenticator.");
        }

        final java.time.LocalDateTime tenMinutesAgo = java.time.LocalDateTime.now().minusMinutes(10);
        boolean hasFreshPending = user.getPendingTwoFactorSecretEncrypted() != null
                && user.getPendingTwoFactorSetupAt() != null
                && user.getPendingTwoFactorSetupAt().isAfter(tenMinutesAgo);

        String secret;
        if (hasFreshPending) {
            // Reuse the same pending secret — the QR code shown to the user stays valid.
            secret = encryptionUtil.decrypt(user.getPendingTwoFactorSecretEncrypted());
            log.debug("[TWO_FA_SETUP_DEBUG] userId={} email={} role={} secretGenerated=false existingPendingSecret=true issuer=RentCar account={} period=30 digits=6 algorithm=SHA1",
                    user.getId(), user.getEmail(), user.getRole(), user.getEmail());
        } else {
            // Generate a new secret and store it in the pending fields.
            secret = generateSecret();
            user.setPendingTwoFactorSecretEncrypted(encryptionUtil.encrypt(secret));
            user.setPendingTwoFactorSetupAt(java.time.LocalDateTime.now());
            userRepository.save(user);
            log.debug("[TWO_FA_SETUP_DEBUG] userId={} email={} role={} secretGenerated=true existingPendingSecret=false issuer=RentCar account={} period=30 digits=6 algorithm=SHA1",
                    user.getId(), user.getEmail(), user.getRole(), user.getEmail());
        }

        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("provisioningUri", provisioningUri(user, secret));
        result.put("manualSecret", formatSecret(secret));
        result.put("issuer", "RentCar");
        result.put("account", user.getEmail());
        return result;
    }

    /**
     * Confirms the 2FA setup by verifying the first TOTP code against the
     * pending secret. On success: moves the pending secret to the active field,
     * clears pending state, enables 2FA, and returns recovery codes (once only).
     *
     * <p>Throws {@link IllegalStateException} with message {@code "SETUP_EXPIRED"}
     * if the pending setup is older than 10 minutes.
     * Throws {@link IllegalArgumentException} with message {@code "INVALID_2FA_CODE"}
     * if the code does not match.
     */
    @Transactional
    public List<String> confirmSetup(User user, String code) {
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException("Two-factor authentication is already enabled.");
        }
        if (user.getPendingTwoFactorSecretEncrypted() == null) {
            throw new IllegalStateException(
                    "No pending 2FA setup found. Please click 'Set up authenticator' to start.");
        }

        // Enforce 10-minute expiry on the pending setup.
        final java.time.LocalDateTime tenMinutesAgo = java.time.LocalDateTime.now().minusMinutes(10);
        if (user.getPendingTwoFactorSetupAt() == null
                || user.getPendingTwoFactorSetupAt().isBefore(tenMinutesAgo)) {
            user.setPendingTwoFactorSecretEncrypted(null);
            user.setPendingTwoFactorSetupAt(null);
            userRepository.save(user);
            throw new IllegalStateException("SETUP_EXPIRED");
        }

        String pendingSecret = encryptionUtil.decrypt(user.getPendingTwoFactorSecretEncrypted());
        String normalizedCode = code == null ? "" : code.trim().replace(" ", "");
        boolean verificationResult = verifyCode(pendingSecret, normalizedCode);

        log.debug("[TWO_FA_CONFIRM_DEBUG] userId={} email={} role={} codeLength={} codeDigitsOnly={} pendingSecretExists=true enabledBefore=false timeStep={} driftAllowed=1 verificationResult={} errorCode={}",
                user.getId(), user.getEmail(), user.getRole(),
                normalizedCode.length(),
                normalizedCode.matches("\\d+"),
                System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS,
                verificationResult,
                verificationResult ? "NONE" : "INVALID_2FA_CODE");

        if (!verificationResult) {
            throw new IllegalArgumentException("INVALID_2FA_CODE");
        }

        // Promote pending → active in one atomic save.
        List<String> recoveryCodes = generateRecoveryCodes();
        user.setTwoFactorSecretEncrypted(user.getPendingTwoFactorSecretEncrypted()); // already encrypted
        user.setPendingTwoFactorSecretEncrypted(null);
        user.setPendingTwoFactorSetupAt(null);
        user.setTwoFactorMethod(TwoFactorMethod.AUTHENTICATOR);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorConfirmedAt(java.time.LocalDateTime.now());
        user.setTwoFactorRecoveryCodesHash(hashRecoveryCodes(recoveryCodes));
        userRepository.save(user);

        log.info("[2FA_CONFIRM] userId={} email={} recoveryCodes={}", user.getId(), user.getEmail(), RECOVERY_CODE_COUNT);
        return recoveryCodes;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String formatSecret(String secret) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < secret.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(secret.charAt(i));
        }
        return sb.toString().trim();
    }

    private List<String> generateRecoveryCodes() {
        SecureRandom rand = new SecureRandom();
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(generateOneRecoveryCode(rand));
        }
        return codes;
    }

    private String generateOneRecoveryCode(SecureRandom rand) {
        StringBuilder sb = new StringBuilder(11);
        for (int segment = 0; segment < 2; segment++) {
            if (segment > 0) sb.append('-');
            for (int i = 0; i < 5; i++) {
                sb.append(RECOVERY_CHARS.charAt(rand.nextInt(RECOVERY_CHARS.length())));
            }
        }
        return sb.toString(); // e.g. "ABCDE-FG234"
    }

    private String hashRecoveryCodes(List<String> plainCodes) {
        // Strip dashes before hashing so verification can also strip dashes
        return plainCodes.stream()
                .map(c -> passwordEncoder.encode(c.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "")))
                .collect(Collectors.joining(","));
    }

    String generateCode(String secret, long counter) {
        try {
            byte[] key  = base32Decode(secret);
            byte[] data = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash   = mac.doFinal(data);
            int offset    = hash[hash.length - 1] & 0x0f;
            int binary    = ((hash[offset]     & 0x7f) << 24)
                          | ((hash[offset + 1] & 0xff) << 16)
                          | ((hash[offset + 2] & 0xff) << 8)
                          |  (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify authenticator code", e);
        }
    }

    private byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        int outputLength  = normalized.length() * 5 / 8;
        byte[] output     = new byte[outputLength];
        int buffer = 0, bitsLeft = 0, index = 0;
        for (char c : normalized.toCharArray()) {
            int digit = BASE32.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("Invalid Base32 secret");
            buffer    = (buffer << 5) | digit;
            bitsLeft += 5;
            if (bitsLeft >= 8 && index < output.length) {
                output[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return output;
    }

    private String base32Encode(byte[] bytes) {
        StringBuilder output = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte value : bytes) {
            buffer    = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                output.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) output.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 31));
        return output.toString();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private String urlEncodeComponent(String value) {
        // URLEncoder uses application/x-www-form-urlencoded which encodes spaces as '+'.
        // In URI path/query we need RFC 3986 percent-encoding (%20), so replace '+' back.
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
