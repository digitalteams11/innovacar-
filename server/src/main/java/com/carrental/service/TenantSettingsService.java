package com.carrental.service;

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

    @Transactional
    public Map<String, Object> getSettings() {
        return toResponse(getOrCreate());
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
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
        return toResponse(settingsRepository.save(settings));
    }

    private TenantSettings getOrCreate() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return settingsRepository.findByTenantId(tenantId).orElseGet(() -> {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            return settingsRepository.save(TenantSettings.builder().tenant(tenant).build());
        });
    }

    private Map<String, Object> toResponse(TenantSettings settings) {
        Map<String, Object> response = new LinkedHashMap<>();
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
        response.put("updatedAt", settings.getUpdatedAt());
        return response;
    }

    private String stringValue(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private Integer integerValue(Object value) {
        return value == null || value.toString().isBlank() ? null : Integer.valueOf(value.toString());
    }
}
