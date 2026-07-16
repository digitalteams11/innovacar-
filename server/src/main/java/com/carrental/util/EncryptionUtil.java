package com.carrental.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility for sensitive data (API keys, tokens).
 *
 * <p><strong>The secret MUST be stable between restarts.</strong> If it changes,
 * all previously encrypted values (e.g., the stored Gemini API key) become
 * permanently unreadable. Configure {@code APP_ENCRYPTION_SECRET} as a
 * stable environment variable in every deployed environment.
 */
@Slf4j
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${app.encryption.secret:changeme-in-production-32bytes!}")
    private String encryptionSecret;

    private SecretKeySpec secretKey;
    private static final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        long startNanos = System.nanoTime();
        log.info("[STARTUP_STEP_BEGIN] EncryptionUtil.init");
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(encryptionSecret.getBytes(StandardCharsets.UTF_8));
            secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("EncryptionUtil initialized — secret length={}", encryptionSecret.length());
            log.info("[STARTUP_STEP_OK] EncryptionUtil.init durationMs={}",
                    (System.nanoTime() - startNanos) / 1_000_000);
        } catch (Exception e) {
            log.error("[STARTUP_STEP_FAILED] EncryptionUtil.init exceptionClass={}", e.getClass().getName());
            throw new RuntimeException("Failed to initialize encryption", e);
        }
    }

    /** Encrypts plainText with AES-256-GCM. Returns null for blank input. */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(iv.length + encrypted.length);
            buf.put(iv);
            buf.put(encrypted);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts an AES-256-GCM ciphertext produced by {@link #encrypt}.
     * Throws {@link RuntimeException} if decryption fails — use
     * {@link #tryDecrypt} when you need a null-safe version.
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] cipherBytes = new byte[buf.remaining()];
            buf.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Like {@link #decrypt} but returns null instead of throwing.
     * Callers can distinguish three states:
     * <ul>
     *   <li>input null/blank  → null (no key stored)</li>
     *   <li>decrypt throws    → null + log warning (key corrupted / wrong secret)</li>
     *   <li>success           → the plaintext key</li>
     * </ul>
     */
    public String tryDecrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        try {
            return decrypt(encryptedText);
        } catch (Exception e) {
            log.warn("[ENCRYPTION] Decrypt failed for stored value (length={}): {} — secret may have changed or value is corrupted",
                    encryptedText.length(), e.getMessage());
            return null;
        }
    }

    /**
     * Returns true if the stored value can be successfully decrypted to a
     * non-blank string. Never throws.
     */
    public boolean canDecrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) return false;
        String plain = tryDecrypt(encryptedText);
        return plain != null && !plain.isBlank();
    }
}
