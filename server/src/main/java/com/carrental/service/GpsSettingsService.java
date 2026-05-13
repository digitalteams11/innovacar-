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

        if (settings == null) {
            settings = GpsSettings.builder()
                    .tenant(tenant)
                    .connectionStatus("PENDING")
                    .activeDevices(0)
                    .enabled(false)
                    .build();
        }

        // Update fields
        settings.setProvider(request.getProvider().toUpperCase());
        settings.setAppId(request.getAppId());
        settings.setBaseUrl(request.getBaseUrl());
        settings.setDeviceGroupId(request.getDeviceGroupId());
        settings.setWebhookUrl(request.getWebhookUrl());

        if (request.getEnabled() != null) {
            settings.setEnabled(request.getEnabled());
        }

        // Encrypt and store API key only if provided
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            String encrypted = encryptionUtil.encrypt(request.getApiKey());
            settings.setApiKeyEncrypted(encrypted);
        }

        // If enabling, test the connection
        if (Boolean.TRUE.equals(settings.getEnabled())) {
            GpsConnectionTestResponse testResult = gpsProviderService.testConnectionWithStoredSettings(settings);
            if (testResult.isSuccess()) {
                settings.setConnectionStatus("CONNECTED");
                settings.setLastError(null);
                settings.setLastSyncAt(LocalDateTime.now());
            } else {
                settings.setConnectionStatus("ERROR");
                settings.setLastError(testResult.getMessage());
            }
        } else {
            settings.setConnectionStatus("DISCONNECTED");
        }

        GpsSettings saved = gpsSettingsRepository.save(settings);
        log.info("GPS settings saved for tenant [{}] provider={} status={}",
                tenantId, saved.getProvider(), saved.getConnectionStatus());

        return GpsSettingsResponse.from(saved);
    }

    @Transactional
    public void deleteSettings() {
        Long tenantId = TenantContext.getCurrentTenantId();
        GpsSettings settings = gpsSettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("GPS settings not found for tenant: " + tenantId));
        gpsSettingsRepository.delete(settings);
        log.info("GPS settings deleted for tenant [{}]", tenantId);
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
        if ("CONNECTED".equals(status)) {
            settings.setLastSyncAt(LocalDateTime.now());
        }

        GpsSettings saved = gpsSettingsRepository.save(settings);
        return GpsSettingsResponse.from(saved);
    }
}
