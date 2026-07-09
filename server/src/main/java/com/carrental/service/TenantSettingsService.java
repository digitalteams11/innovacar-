package com.carrental.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.carrental.entity.Tenant;
import com.carrental.entity.TenantSettings;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.TenantSettingsRepository;
import com.carrental.security.TenantContext;
import com.carrental.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TenantSettingsService {
    private final TenantSettingsRepository settingsRepository;
    private final TenantRepository tenantRepository;
    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> getSettings() {
        if (TenantContext.getCurrentTenantId() == null) {
            return envelope("Tenant settings loaded successfully", defaultSettingsData(null));
        }
        return envelope("Settings loaded successfully", toResponse(getOrCreate()));
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        if (TenantContext.getCurrentTenantId() == null) {
            Map<String, Object> defaults = defaultSettingsData(null);
            if (body != null) defaults.putAll(body);
            return envelope("Settings saved successfully", defaults);
        }
        TenantSettings settings = getOrCreate();
        if (body.containsKey("currency")) {
            String currency = body.get("currency").toString().toUpperCase(Locale.ROOT);
            Currency.getInstance(currency);
            settings.setCurrency(currency);
        }
        if (body.containsKey("language")) {
            String language = body.get("language").toString().toLowerCase(Locale.ROOT);
            if (!Set.of("en", "fr", "ar").contains(language)) {
                throw new IllegalArgumentException("Unsupported language");
            }
            settings.setLanguage(language);
        }
        if (body.containsKey("timezone")) {
            String timezone = body.get("timezone").toString();
            ZoneId.of(timezone);
            settings.setTimezone(timezone);
        }
        if (body.containsKey("smtpHost")) settings.setSmtpHost(stringValue(body.get("smtpHost")));
        if (body.containsKey("smtpPort")) settings.setSmtpPort(integerValue(body.get("smtpPort")));
        if (body.containsKey("smtpUsername")) settings.setSmtpUsername(stringValue(body.get("smtpUsername")));
        if (body.containsKey("smtpPassword") && body.get("smtpPassword") != null
                && !body.get("smtpPassword").toString().isBlank()) {
            settings.setSmtpPasswordEncrypted(encryptionUtil.encrypt(body.get("smtpPassword").toString()));
        }
        if (body.containsKey("smtpTls")) settings.setSmtpTls(Boolean.valueOf(body.get("smtpTls").toString()));
        if (body.containsKey("notificationInApp")) settings.setNotificationInApp(Boolean.valueOf(body.get("notificationInApp").toString()));
        if (body.containsKey("notificationEmail")) settings.setNotificationEmail(Boolean.valueOf(body.get("notificationEmail").toString()));
        if (body.containsKey("notificationPush")) settings.setNotificationPush(Boolean.valueOf(body.get("notificationPush").toString()));
        if (body.containsKey("inspectionRetentionDays")) {
            int days = integerValue(body.get("inspectionRetentionDays"));
            if (days < 1 || days > 365) {
                throw new IllegalArgumentException("Inspection retention must be between 1 and 365 days");
            }
            settings.setInspectionRetentionDays(days);
        }
        if (body.containsKey("soundSettings") && body.get("soundSettings") instanceof Map<?, ?> soundSettings) {
            try {
                settings.setSoundSettingsJson(objectMapper.writeValueAsString(soundSettings));
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Invalid sound settings");
            }
        }
        if (body.containsKey("securitySettings") && body.get("securitySettings") instanceof Map<?, ?> securitySettings) {
            try {
                settings.setSecuritySettingsJson(objectMapper.writeValueAsString(securitySettings));
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Invalid security settings");
            }
        }
        if (body.containsKey("appearance") && body.get("appearance") instanceof Map<?, ?> appearance) {
            try {
                settings.setAppearanceJson(objectMapper.writeValueAsString(appearance));
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Invalid appearance settings");
            }
        }
        return envelope("Settings saved successfully", toResponse(settingsRepository.save(settings)));
    }

    private synchronized TenantSettings getOrCreate() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required to persist tenant settings");
        }
        return settingsRepository.findByTenantId(tenantId).orElseGet(() -> {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            return settingsRepository.save(TenantSettings.builder().tenant(tenant).build());
        });
    }

    private Map<String, Object> toResponse(TenantSettings settings) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("agencyName", settings.getTenant() != null ? settings.getTenant().getName() : "RentCar");
        response.put("logo", null);
        response.put("currency", settings.getCurrency());
        response.put("language", settings.getLanguage());
        response.put("timezone", settings.getTimezone());
        response.put("smtpHost", settings.getSmtpHost() != null ? settings.getSmtpHost() : "");
        response.put("smtpPort", settings.getSmtpPort() != null ? settings.getSmtpPort() : 587);
        response.put("smtpUsername", settings.getSmtpUsername() != null ? settings.getSmtpUsername() : "");
        response.put("hasSmtpPassword", settings.getSmtpPasswordEncrypted() != null);
        response.put("smtpTls", settings.getSmtpTls());
        response.put("notificationInApp", settings.getNotificationInApp());
        response.put("notificationEmail", settings.getNotificationEmail());
        response.put("notificationPush", settings.getNotificationPush());
        response.put("inspectionRetentionDays", settings.getInspectionRetentionDays());
        Map<String, Object> appearance = readAppearance(settings.getAppearanceJson());
        response.put("appearance", appearance);
        response.put("theme", appearance.getOrDefault("mode", "light"));
        response.put("glassIntensity", appearance.getOrDefault("glassIntensity", 40));
        response.put("primaryColor", appearance.getOrDefault("primaryColor", "#0f172a"));
        response.put("accentColor", appearance.getOrDefault("accentColor", "#10b981"));
        response.put("defaultSignature", settings.getTenant() != null ? settings.getTenant().getAgencySignature() : null);
        response.put("soundSettings", readSoundSettings(settings.getSoundSettingsJson()));
        response.put("securitySettings", readSettings(settings.getSecuritySettingsJson()));
        response.put("updatedAt", settings.getUpdatedAt());
        return response;
    }

    private Map<String, Object> readAppearance(String json) {
        if (json == null || json.isBlank()) return defaultAppearance();
        Map<String, Object> parsed = readSettings(json);
        Map<String, Object> merged = new LinkedHashMap<>(defaultAppearance());
        merged.putAll(parsed);
        return merged;
    }

    private Map<String, Object> readSoundSettings(String json) {
        if (json == null || json.isBlank()) return defaultSoundSettings();
        Map<String, Object> parsed = readSettings(json);
        Map<String, Object> merged = new LinkedHashMap<>(defaultSoundSettings());
        merged.putAll(parsed);
        if (parsed.get("events") instanceof Map<?, ?> parsedEvents) {
            Map<String, Object> events = new LinkedHashMap<>((Map<String, Object>) defaultSoundSettings().get("events"));
            parsedEvents.forEach((key, value) -> events.put(String.valueOf(key), value));
            merged.put("events", events);
        }
        return merged;
    }

    private Map<String, Object> readSettings(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    public Map<String, Object> data(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return response;
    }

    public Map<String, Object> envelope(String message, Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>(data);
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    public Map<String, Object> error(String message, String errorCode) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("errorCode", errorCode);
        response.put("data", null);
        return response;
    }

    private Map<String, Object> defaultAppearance() {
        Map<String, Object> appearance = new LinkedHashMap<>();
        appearance.put("mode", "auto");
        appearance.put("preset", "neo-emerald");
        appearance.put("primaryColor", "#10B981");
        appearance.put("secondaryColor", "#0F766E");
        appearance.put("accentColor", "#F59E0B");
        appearance.put("sidebarColor", "#064E3B");
        appearance.put("backgroundColor", "#F8FAFC");
        appearance.put("surfaceColor", "#FFFFFF");
        appearance.put("glassColor", "#FFFFFF");
        appearance.put("glassIntensity", 40);
        appearance.put("blur", 24);
        appearance.put("opacity", 76);
        appearance.put("depth", 55);
        appearance.put("shadowStrength", 42);
        appearance.put("animationSpeed", 100);
        appearance.put("cornerRadius", 8);
        appearance.put("fontFamily", "Inter");
        appearance.put("cardDensity", "comfortable");
        appearance.put("buttonStyle", "solid");
        appearance.put("sidebarStyle", "floating");
        appearance.put("whiteLabelMode", false);
        return appearance;
    }

    private Map<String, Object> defaultSoundSettings() {
        Map<String, Object> events = new LinkedHashMap<>();
        events.put("newReservation", true);
        events.put("contractSigned", true);
        events.put("paymentReceived", true);
        events.put("vehicleReturned", true);
        events.put("gpsAlert", true);
        events.put("subscriptionExpiring", true);
        events.put("error", true);
        events.put("supportMessage", true);
        events.put("NEW_RESERVATION", true);
        events.put("CONTRACT_SIGNED", true);
        events.put("PAYMENT_RECEIVED", true);
        events.put("VEHICLE_RETURNED", true);
        events.put("GPS_ALERT", true);
        events.put("ERROR", true);
        events.put("SUPPORT_MESSAGE", true);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", true);
        settings.put("volume", 30);
        settings.put("theme", "automotive");
        settings.put("muteNightMode", false);
        settings.put("muteSystemAlerts", false);
        settings.put("importantOnly", false);
        settings.put("doNotDisturb", false);
        settings.put("events", events);
        return settings;
    }

    private Map<String, Object> defaultSettingsData(Tenant tenant) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> appearance = defaultAppearance();
        data.put("agencyName", tenant != null ? tenant.getName() : "RentCar");
        data.put("logo", null);
        data.put("currency", "MAD");
        data.put("language", "en");
        data.put("timezone", "Africa/Casablanca");
        data.put("theme", appearance.get("mode"));
        data.put("glassIntensity", appearance.get("glassIntensity"));
        data.put("primaryColor", appearance.get("primaryColor"));
        data.put("accentColor", appearance.get("accentColor"));
        data.put("defaultSignature", tenant != null ? tenant.getAgencySignature() : null);
        data.put("appearance", appearance);
        data.put("soundSettings", defaultSoundSettings());
        data.put("securitySettings", Map.of());
        data.put("inspectionRetentionDays", 7);
        data.put("notificationInApp", true);
        data.put("notificationEmail", true);
        data.put("notificationPush", false);
        return data;
    }

    private String stringValue(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private Integer integerValue(Object value) {
        return value == null || value.toString().isBlank() ? null : Integer.valueOf(value.toString());
    }
}
