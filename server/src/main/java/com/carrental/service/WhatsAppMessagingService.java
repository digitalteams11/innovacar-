package com.carrental.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The one canonical WhatsApp entry point — mirrors {@link SmtpMailService}'s
 * role for email. Never throws: a missing provider configuration or a failed
 * send is reported as a result so it can never break the business operation
 * (e.g. creating a client-information request) that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMessagingService {

    private final WhatsAppProvider provider;

    public record WhatsAppResult(boolean sent, boolean configured, String providerUsed, String errorMessage, String errorCode) {
        public static WhatsAppResult notConfigured(String provider, String message) {
            return new WhatsAppResult(false, false, provider, message, "WHATSAPP_NOT_CONFIGURED");
        }
        public static WhatsAppResult failure(String provider, String message, String code) {
            return new WhatsAppResult(false, true, provider, message, code);
        }
        public static WhatsAppResult success(String provider) {
            return new WhatsAppResult(true, true, provider, null, null);
        }
    }

    public WhatsAppResult send(String toE164Phone, String body) {
        WhatsAppResult result = provider.send(new WhatsAppMessage(toE164Phone, body));
        if (result.sent()) {
            log.info("[WHATSAPP] Delivered via {}", result.providerUsed());
        } else {
            log.warn("[WHATSAPP] Not delivered via {} | errorCode={} | reason={}", result.providerUsed(), result.errorCode(), result.errorMessage());
        }
        return result;
    }

    public boolean isConfigured() {
        return provider.isConfigured();
    }
}
