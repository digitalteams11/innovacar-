package com.carrental.service;

import com.carrental.entity.PlatformSettings;
import com.carrental.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves which internal inbox a Support Center ticket should be routed to,
 * based on its channel/category, using the Super Admin-configured routing
 * emails on {@link PlatformSettings}. The frontend never decides the
 * destination — this is the single source of truth for routing rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportRoutingService {

    public static final String CHANNEL_CONTACT   = "CONTACT";
    public static final String CHANNEL_SUPPORT   = "SUPPORT";
    public static final String CHANNEL_TECHNICAL = "TECHNICAL";
    public static final String CHANNEL_BILLING   = "BILLING";
    public static final String CHANNEL_SECURITY  = "SECURITY";

    private final PlatformSettingsRepository platformSettingsRepository;

    /**
     * Resolves the destination email for a ticket, following the channel/category
     * routing rules. Falls back through supportEmail then fallbackEmail/contactEmail
     * when a more specific inbox is not configured.
     */
    public String resolveDestinationEmail(String channel, String category) {
        PlatformSettings settings = platformSettingsRepository.findTopByOrderByIdAsc().orElse(null);
        if (settings == null) {
            log.warn("[SUPPORT_ROUTING] No PlatformSettings row found — cannot resolve destination email.");
            return null;
        }

        String cat = category != null ? category.toUpperCase() : "";
        String chan = channel != null ? channel.toUpperCase() : inferChannel(cat);

        String resolved = switch (chan) {
            case CHANNEL_TECHNICAL -> firstNonBlank(settings.getTechnicalEmail(), settings.getSupportEmail());
            case CHANNEL_BILLING -> firstNonBlank(settings.getBillingEmail(), settings.getSupportEmail());
            case CHANNEL_SECURITY -> firstNonBlank(settings.getSecurityEmail(), settings.getSupportEmail());
            case CHANNEL_SUPPORT -> switch (cat) {
                case "BILLING", "SUBSCRIPTION", "PAYMENT" -> firstNonBlank(settings.getBillingEmail(), settings.getSupportEmail());
                case "SECURITY" -> firstNonBlank(settings.getSecurityEmail(), settings.getSupportEmail());
                default -> settings.getSupportEmail();
            };
            case CHANNEL_CONTACT -> settings.getContactEmail();
            default -> null;
        };

        return firstNonBlank(resolved, settings.getFallbackEmail(), settings.getContactEmail(), settings.getSupportEmail());
    }

    /** Infers a channel from a category when the caller only knows the category (legacy tickets). */
    private String inferChannel(String category) {
        return switch (category) {
            case "GPS", "EMAIL_SMTP", "PDF", "BUG", "API", "SYSTEM_DOWN", "DATA_RESET" -> CHANNEL_TECHNICAL;
            case "BILLING", "SUBSCRIPTION", "PAYMENT" -> CHANNEL_BILLING;
            case "SECURITY" -> CHANNEL_SECURITY;
            case "GENERAL", "SALES", "DEMO", "PARTNERSHIP" -> CHANNEL_CONTACT;
            case "ACCOUNT", "LOGIN", "CONTRACT", "RESERVATION", "VEHICLE" -> CHANNEL_SUPPORT;
            default -> CHANNEL_SUPPORT;
        };
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }
}
