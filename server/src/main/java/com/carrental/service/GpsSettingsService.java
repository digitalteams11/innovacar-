package com.carrental.service;

import com.carrental.dto.gps.*;
import com.carrental.entity.GpsSettings;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.GpsSettingsRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import com.carrental.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Service for managing GPS provider settings per tenant.
 * Handles secure storage and retrieval of API credentials.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GpsSettingsService {

    private final GpsSettingsRepository gpsSettingsRepository;
    private final TenantRepository tenantRepository;
    private final EncryptionUtil encryptionUtil;
    private final GpsProviderService gpsProviderService;

    @Transactional(readOnly = true)
    public GpsSettingsResponse getSettings() {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsSettings settings = gpsSettingsRepository.findByTenantId(tenantId)
                .orElse(null);

        if (settings == null) {
            // Return a default empty response
            return GpsSettingsResponse.builder()
                    .provider("")
                    .connectionStatus("DISCONNECTED")
                    .activeDevices(0)
                    .enabled(false)
                    .hasCredentials(false)
                    .build();
        }

        return GpsSettingsResponse.from(settings);
    }

    @Transactional
    public GpsSettingsResponse saveSettings(GpsSettingsRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        GpsSettings settings = gpsSettingsRepository.findByTenantId(tenantId)
                .orElse(null);

        boolean hadCredentialsBefore = settings != null
                && ((settings.getApiKeyEncrypted() != null && !settings.getApiKeyEncrypted().isBlank())
                    || (settings.getEncryptedPassword() != null && !settings.getEncryptedPassword().isBlank()));
        boolean providingNewKey      = request.getApiKey()  != null && !request.getApiKey().isBlank();
        boolean providingNewPassword = request.getPassword() != null && !request.getPassword().isBlank();

        if (Boolean.TRUE.equals(request.getEnabled()) && !hadCredentialsBefore && !providingNewKey && !providingNewPassword) {
            throw new IllegalArgumentException("API key or password is required to connect this GPS provider.");
        }
        String normalizedBaseUrl = normalizeBaseUrl(request.getBaseUrl());

        if (settings == null) {
            settings = GpsSettings.builder()
                    .tenant(tenant)
                    .connectionStatus("DISCONNECTED")
                    .activeDevices(0)
                    .enabled(false)
                    .build();
        }

        // Update fields
        settings.setProvider(request.getProvider().toUpperCase());
        settings.setAppId(request.getAppId());
        settings.setBaseUrl(normalizedBaseUrl);
        settings.setDeviceGroupId(request.getDeviceGroupId());
        settings.setWebhookUrl(request.getWebhookUrl());

        if (request.getEnabled() != null) {
            settings.setEnabled(request.getEnabled());
        }

        // Geofence & alert configuration
        if (request.getCityLat() != null) settings.setCityLat(request.getCityLat());
        if (request.getCityLng() != null) settings.setCityLng(request.getCityLng());
        if (request.getRadiusKm() != null) settings.setRadiusKm(request.getRadiusKm());
        if (request.getMovementThresholdM() != null) settings.setMovementThresholdM(request.getMovementThresholdM());
        if (request.getInactivityTimeoutMin() != null) settings.setInactivityTimeoutMin(request.getInactivityTimeoutMin());
        if (request.getNotifyMovement() != null) settings.setNotifyMovement(request.getNotifyMovement());
        if (request.getNotifyGeofence() != null) settings.setNotifyGeofence(request.getNotifyGeofence());
        if (request.getNotifyOffline() != null) settings.setNotifyOffline(request.getNotifyOffline());
        if (request.getPollingIntervalSec() != null) settings.setPollingIntervalSec(request.getPollingIntervalSec());

        // Encrypt and store API key only if provided
        if (providingNewKey) {
            settings.setApiKeyEncrypted(encryptionUtil.encrypt(request.getApiKey()));
        }

        // Encrypt and store password only if provided (Traccar basic auth)
        if (providingNewPassword) {
            settings.setEncryptedPassword(encryptionUtil.encrypt(request.getPassword()));
        }

        // Auth header fields for Custom API (stored as plain text — not sensitive)
        if (request.getAuthHeaderName() != null) {
            settings.setAuthHeaderName(request.getAuthHeaderName().isBlank() ? null : request.getAuthHeaderName().trim());
        }
        if (request.getAuthPrefix() != null) {
            settings.setAuthPrefix(request.getAuthPrefix().isBlank() ? null : request.getAuthPrefix().trim());
        }

        boolean hasCredentialsNow = (settings.getApiKeyEncrypted() != null && !settings.getApiKeyEncrypted().isBlank())
                || (settings.getEncryptedPassword() != null && !settings.getEncryptedPassword().isBlank());

        // If enabling, test the connection for real — never assume success.
        if (Boolean.TRUE.equals(settings.getEnabled())) {
            GpsConnectionTestResponse testResult = gpsProviderService.testConnectionWithStoredSettings(settings);
            settings.setLastTestedAt(LocalDateTime.now());
            if (testResult.isSuccess()) {
                settings.setConnectionStatus("CONNECTED");
                settings.setLastError(null);
                settings.setLastSyncAt(LocalDateTime.now());
            } else {
                settings.setConnectionStatus("FAILED");
                settings.setLastError(testResult.getMessage());
            }
        } else {
            // Saved but not (yet) enabled/tested: CONFIGURED if credentials are
            // stored, otherwise there's nothing to connect to yet.
            settings.setConnectionStatus(hasCredentialsNow ? "CONFIGURED" : "DISCONNECTED");
        }

        GpsSettings saved = gpsSettingsRepository.save(settings);
        log.info("GPS settings saved for tenant [{}] provider={} status={}",
                tenantId, saved.getProvider(), saved.getConnectionStatus());

        return GpsSettingsResponse.from(saved);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String trimmed = baseUrl.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("Base URL is invalid. Example: https://api.provider.com");
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** Full reset: removes the GPS settings row entirely ("Remove Integration"). */
    @Transactional
    public void deleteSettings() {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsSettings settings = gpsSettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GPS settings not found for tenant: " + tenantId));
        gpsSettingsRepository.delete(settings);
        log.info("GPS settings deleted for tenant [{}]", tenantId);
    }

    /**
     * Deactivates GPS tracking without touching stored credentials, so the
     * agency can re-enable later without re-entering their API key.
     */
    @Transactional
    public GpsSettingsResponse deactivate() {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsSettings settings = gpsSettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GPS settings not found for tenant: " + tenantId));
        settings.setEnabled(false);
        settings.setConnectionStatus("DISABLED");
        GpsSettings saved = gpsSettingsRepository.save(settings);
        log.info("GPS integration deactivated for tenant [{}]", tenantId);
        return GpsSettingsResponse.from(saved);
    }

    /**
     * Removes only the stored credentials (API key + APP ID). Provider and
     * Base URL are kept by default so reconnecting doesn't require looking
     * those up again. GPS history elsewhere in the platform is untouched.
     */
    @Transactional
    public GpsSettingsResponse deleteCredentials() {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsSettings settings = gpsSettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GPS settings not found for tenant: " + tenantId));
        settings.setApiKeyEncrypted(null);
        settings.setEncryptedPassword(null);
        settings.setAppId(null);
        settings.setEnabled(false);
        settings.setConnectionStatus("DISCONNECTED");
        settings.setLastError(null);
        GpsSettings saved = gpsSettingsRepository.save(settings);
        log.info("GPS credentials deleted for tenant [{}]", tenantId);
        return GpsSettingsResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public GpsSettings getSettingsEntity() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return gpsSettingsRepository.findByTenantId(tenantId).orElse(null);
    }

    @Transactional
    public GpsSettingsResponse updateConnectionStatus(String status, String errorMessage) {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsSettings settings = gpsSettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GPS settings not found for tenant: " + tenantId));

        settings.setConnectionStatus(status);
        settings.setLastError(errorMessage);
        settings.setLastTestedAt(LocalDateTime.now());
        if ("CONNECTED".equals(status)) {
            settings.setLastSyncAt(LocalDateTime.now());
        }

        GpsSettings saved = gpsSettingsRepository.save(settings);
        return GpsSettingsResponse.from(saved);
    }
}
