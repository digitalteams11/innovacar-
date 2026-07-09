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
    private LocalDateTime lastTestedAt;
    private Integer activeDevices;
    private String lastError;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long tenantId;
    private Boolean hasCredentials; // true if apiKey is stored
    private Boolean hasPassword;    // true if password is stored (Traccar basic auth)
    private String  authHeaderName; // For Custom API
    private String  authPrefix;     // For Custom API

    // Geofence & alert configuration
    private Double cityLat;
    private Double cityLng;
    private Double radiusKm;
    private Integer movementThresholdM;
    private Integer inactivityTimeoutMin;
    private Boolean notifyMovement;
    private Boolean notifyGeofence;
    private Boolean notifyOffline;
    private Integer pollingIntervalSec;

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
                .lastTestedAt(settings.getLastTestedAt())
                .activeDevices(settings.getActiveDevices())
                .lastError(settings.getLastError())
                .enabled(settings.getEnabled())
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .tenantId(settings.getTenant().getId())
                .hasCredentials(settings.getApiKeyEncrypted() != null && !settings.getApiKeyEncrypted().isEmpty())
                .hasPassword(settings.getEncryptedPassword() != null && !settings.getEncryptedPassword().isEmpty())
                .authHeaderName(settings.getAuthHeaderName())
                .authPrefix(settings.getAuthPrefix())
                .cityLat(settings.getCityLat())
                .cityLng(settings.getCityLng())
                .radiusKm(settings.getRadiusKm())
                .movementThresholdM(settings.getMovementThresholdM())
                .inactivityTimeoutMin(settings.getInactivityTimeoutMin())
                .notifyMovement(settings.getNotifyMovement())
                .notifyGeofence(settings.getNotifyGeofence())
                .notifyOffline(settings.getNotifyOffline())
                .pollingIntervalSec(settings.getPollingIntervalSec())
                .build();
    }
}
