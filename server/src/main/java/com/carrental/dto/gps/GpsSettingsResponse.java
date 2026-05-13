package com.carrental.dto.gps;

import com.carrental.entity.GpsSettings;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * GPS provider configuration response (API key is NEVER returned).
 */
@Data
@Builder
public class GpsSettingsResponse {

    private Long id;
    private String provider;
    private String appId;
    private String baseUrl;
    private String deviceGroupId;
    private String webhookUrl;
    private String connectionStatus;
    private LocalDateTime lastSyncAt;
    private Integer activeDevices;
    private String lastError;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long tenantId;
    private Boolean hasCredentials; // true if apiKey is stored

    public static GpsSettingsResponse from(GpsSettings settings) {
        return GpsSettingsResponse.builder()
                .id(settings.getId())
                .provider(settings.getProvider())
                .appId(settings.getAppId())
                .baseUrl(settings.getBaseUrl())
                .deviceGroupId(settings.getDeviceGroupId())
                .webhookUrl(settings.getWebhookUrl())
                .connectionStatus(settings.getConnectionStatus())
                .lastSyncAt(settings.getLastSyncAt())
                .activeDevices(settings.getActiveDevices())
                .lastError(settings.getLastError())
                .enabled(settings.getEnabled())
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .tenantId(settings.getTenant().getId())
                .hasCredentials(settings.getApiKeyEncrypted() != null && !settings.getApiKeyEncrypted().isEmpty())
                .build();
    }
}
