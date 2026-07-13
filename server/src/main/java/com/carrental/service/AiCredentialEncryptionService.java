package com.carrental.service;

import com.carrental.entity.AiProviderType;
import com.carrental.exception.AiServiceException;
import com.carrental.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper over the existing {@link EncryptionUtil} (AES-256-GCM, keyed
 * from {@code APP_ENCRYPTION_SECRET}) so AI provider credentials reuse the
 * platform's one encryption primitive instead of introducing a second key.
 */
@Service
@RequiredArgsConstructor
public class AiCredentialEncryptionService {

    private final EncryptionUtil encryptionUtil;

    public String encrypt(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        return encryptionUtil.encrypt(rawKey.trim());
    }

    /** Throws {@link AiServiceException#keyDecryptionFailed()} rather than a raw runtime exception. */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        String plain = encryptionUtil.tryDecrypt(encrypted);
        if (plain == null) {
            throw AiServiceException.keyDecryptionFailed();
        }
        return plain;
    }

    public boolean isConfigured(String encrypted) {
        return encryptionUtil.canDecrypt(encrypted);
    }

    /**
     * Computed once at save time from the raw key — never requires decrypting
     * on read. Format: {@code <providerPrefix>••••••••••••<last4>}.
     */
    public String mask(String rawKey, AiProviderType providerType) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String trimmed = rawKey.trim();
        String prefix = knownPrefix(providerType, trimmed);
        String last4 = trimmed.length() >= 4 ? trimmed.substring(trimmed.length() - 4) : trimmed;
        return prefix + "••••••••••••" + last4;
    }

    private String knownPrefix(AiProviderType providerType, String rawKey) {
        return switch (providerType) {
            case GROQ -> "gsk_";
            case OPENAI -> "sk-";
            case OPENROUTER -> "sk-or-";
            case GEMINI -> "AIza";
            default -> rawKey.length() >= 4 ? rawKey.substring(0, 4) : rawKey;
        };
    }
}
