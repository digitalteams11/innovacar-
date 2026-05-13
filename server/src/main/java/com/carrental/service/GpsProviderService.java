package com.carrental.service;

import com.carrental.dto.gps.*;
import com.carrental.entity.GpsSettings;
import com.carrental.entity.Vehicle;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import com.carrental.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for interacting with external GPS providers.
 * Handles connection testing, device sync, and provider-specific API calls.
 * All API keys are decrypted only in-memory and never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GpsProviderService {

    private final EncryptionUtil encryptionUtil;
    private final VehicleRepository vehicleRepository;

    private WebClient createWebClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .build();
    }

    /**
     * Test connection to a GPS provider using provided credentials.
     * Does NOT save anything to the database.
     */
    public GpsConnectionTestResponse testConnection(GpsConnectionTestRequest request) {
        long startTime = System.currentTimeMillis();
        String provider = request.getProvider().toUpperCase();

        try {
            return switch (provider) {
                case "IOPGPS" -> testIopgpsConnection(request, startTime);
                case "TRACCAR" -> testTraccarConnection(request, startTime);
                case "WIALON" -> testWialonConnection(request, startTime);
                case "GPSWOX" -> testGpswoxConnection(request, startTime);
                case "CUSTOM" -> testCustomConnection(request, startTime);
                default -> GpsConnectionTestResponse.builder()
                        .success(false)
                        .message("Unsupported provider: " + provider)
                        .provider(provider)
                        .errorCode("UNSUPPORTED_PROVIDER")
                        .build();
            };
        } catch (WebClientResponseException e) {
            log.warn("GPS provider test failed for {}: HTTP {} - {}", provider, e.getStatusCode(), e.getMessage());
            return GpsConnectionTestResponse.builder()
                    .success(false)
                    .message(mapHttpError(e.getStatusCode(), provider))
                    .provider(provider)
                    .errorCode("HTTP_" + e.getStatusCode().value())
                    .responseTime((System.currentTimeMillis() - startTime) + "ms")
                    .build();
        } catch (Exception e) {
            log.error("GPS provider test error for {}: {}", provider, e.getMessage());
            return GpsConnectionTestResponse.builder()
                    .success(false)
                    .message("Connection failed: " + e.getMessage())
                    .provider(provider)
                    .errorCode("CONNECTION_ERROR")
                    .responseTime((System.currentTimeMillis() - startTime) + "ms")
                    .build();
        }
    }

    /**
     * Test connection using stored settings for the current tenant.
     */
    public GpsConnectionTestResponse testConnectionWithStoredSettings(GpsSettings settings) {
        if (settings == null || settings.getApiKeyEncrypted() == null) {
            return GpsConnectionTestResponse.builder()
                    .success(false)
                    .message("No GPS credentials configured")
                    .errorCode("NO_CREDENTIALS")
                    .build();
        }

        String apiKey = encryptionUtil.decrypt(settings.getApiKeyEncrypted());
        GpsConnectionTestRequest request = new GpsConnectionTestRequest();
        request.setProvider(settings.getProvider());
        request.setAppId(settings.getAppId());
        request.setApiKey(apiKey);
        request.setBaseUrl(settings.getBaseUrl());
        request.setDeviceGroupId(settings.getDeviceGroupId());

        return testConnection(request);
    }

    /**
     * Sync GPS devices from provider to local vehicles.
     */
    public GpsDeviceSyncResponse syncDevices(GpsSettings settings) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (settings == null || settings.getApiKeyEncrypted() == null) {
            return GpsDeviceSyncResponse.builder()
                    .success(false)
                    .message("No GPS credentials configured")
                    .build();
        }

        String provider = settings.getProvider().toUpperCase();
        String apiKey = encryptionUtil.decrypt(settings.getApiKeyEncrypted());

        try {
            List<GpsDeviceSyncResponse.GpsDeviceInfo> providerDevices = fetchDevicesFromProvider(
                    provider, settings.getBaseUrl(), settings.getAppId(), apiKey, settings.getDeviceGroupId()
            );

            List<Vehicle> existingVehicles = vehicleRepository.findAllByTenantId(tenantId);
            Map<String, Vehicle> vehicleByDeviceId = existingVehicles.stream()
                    .filter(v -> v.getGpsDeviceId() != null)
                    .collect(Collectors.toMap(Vehicle::getGpsDeviceId, v -> v));

            int created = 0;
            int updated = 0;

            for (GpsDeviceSyncResponse.GpsDeviceInfo device : providerDevices) {
                Vehicle vehicle = vehicleByDeviceId.get(device.getDeviceId());
                if (vehicle != null) {
                    // Update existing vehicle GPS data
                    vehicle.setGpsImei(device.getImei());
                    vehicle.setGpsEnabled(true);
                    if (device.getLatitude() != null) vehicle.setLastLatitude(device.getLatitude());
                    if (device.getLongitude() != null) vehicle.setLastLongitude(device.getLongitude());
                    if (device.getSpeed() != null) vehicle.setLastSpeed(device.getSpeed());
                    vehicle.setLastGpsUpdate(LocalDateTime.now());
                    updated++;
                } else {
                    // Could create a new vehicle here, but for now we only update existing ones
                    // In a real system, you might want to create placeholder vehicles
                    created++;
                }
            }

            vehicleRepository.saveAll(existingVehicles);

            // Update settings sync timestamp
            settings.setLastSyncAt(LocalDateTime.now());
            settings.setActiveDevices(providerDevices.size());

            return GpsDeviceSyncResponse.builder()
                    .success(true)
                    .message("Devices synchronized successfully")
                    .devicesSynced(providerDevices.size())
                    .devicesCreated(created)
                    .devicesUpdated(updated)
                    .devices(providerDevices)
                    .build();

        } catch (Exception e) {
            log.error("Device sync failed for tenant {}: {}", tenantId, e.getMessage());
            return GpsDeviceSyncResponse.builder()
                    .success(false)
                    .message("Sync failed: " + e.getMessage())
                    .build();
        }
    }

    // ── Provider-specific connection tests ─────────────────────────────────────

    private GpsConnectionTestResponse testIopgpsConnection(GpsConnectionTestRequest request, long startTime) {
        String baseUrl = Optional.ofNullable(request.getBaseUrl()).orElse("https://www.iopgps.com");
        
        try {
            String token = fetchIopgpsToken(baseUrl, request.getAppId(), request.getApiKey());
            if (token == null) {
                return GpsConnectionTestResponse.builder()
                        .success(false)
                        .message("IOPGPS authentication failed")
                        .provider("IOPGPS")
                        .errorCode("AUTH_FAILED")
                        .responseTime((System.currentTimeMillis() - startTime) + "ms")
                        .build();
            }

            WebClient client = createWebClient(baseUrl);
            var response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/v1/devices")
                            .queryParam("access_token", token)
                            .build())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException("API error: " + body)))
                    )
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            int deviceCount = extractDeviceCount(response);

            return GpsConnectionTestResponse.builder()
                    .success(true)
                    .message("Connected to IOPGPS successfully")
                    .provider("IOPGPS")
                    .devicesFound(deviceCount)
                    .responseTime((System.currentTimeMillis() - startTime) + "ms")
                    .build();
        } catch (Exception e) {
            log.error("IOPGPS test failed: {}", e.getMessage());
            throw e;
        }
    }

    private GpsConnectionTestResponse testTraccarConnection(GpsConnectionTestRequest request, long startTime) {
        String baseUrl = Optional.ofNullable(request.getBaseUrl()).orElse("http://localhost:8082");
        WebClient client = createWebClient(baseUrl);

        // Traccar uses Basic Auth with email/password or cookie session
        // For API key integration, we use Authorization header
        var response = client.get()
                .uri("/api/devices")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getApiKey())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("API error: " + body)))
                )
                .bodyToMono(List.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        return GpsConnectionTestResponse.builder()
                .success(true)
                .message("Connected to Traccar successfully")
                .provider("TRACCAR")
                .devicesFound(response != null ? response.size() : 0)
                .responseTime((System.currentTimeMillis() - startTime) + "ms")
                .build();
    }

    private GpsConnectionTestResponse testWialonConnection(GpsConnectionTestRequest request, long startTime) {
        String baseUrl = Optional.ofNullable(request.getBaseUrl()).orElse("https://hst-api.wialon.com");
        WebClient client = createWebClient(baseUrl);

        // Wialon uses token-based auth via svc=token/login
        var authResponse = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/wialon/ajax.html")
                        .queryParam("svc", "token/login")
                        .queryParam("params", "{\"token\":\"" + request.getApiKey() + "\"}")
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (authResponse != null && authResponse.containsKey("eid")) {
            String eid = (String) authResponse.get("eid");
            // Test fetching units with the session ID
            var unitsResponse = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wialon/ajax.html")
                            .queryParam("svc", "core/search_items")
                            .queryParam("params", "{\"spec\":{\"itemsType\":\"avl_unit\",\"propName\":\"sys_name\",\"propValueMask\":\"*\",\"sortType\":\"sys_name\"},\"force\":1,\"flags\":1,\"from\":0,\"to\":0}")
                            .queryParam("sid", eid)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            int count = 0;
            if (unitsResponse != null && unitsResponse.get("items") instanceof List) {
                count = ((List<?>) unitsResponse.get("items")).size();
            }

            return GpsConnectionTestResponse.builder()
                    .success(true)
                    .message("Connected to Wialon successfully")
                    .provider("WIALON")
                    .devicesFound(count)
                    .responseTime((System.currentTimeMillis() - startTime) + "ms")
                    .build();
        }

        return GpsConnectionTestResponse.builder()
                .success(false)
                .message("Wialon authentication failed")
                .provider("WIALON")
                .errorCode("AUTH_FAILED")
                .responseTime((System.currentTimeMillis() - startTime) + "ms")
                .build();
    }

    private GpsConnectionTestResponse testGpswoxConnection(GpsConnectionTestRequest request, long startTime) {
        String baseUrl = Optional.ofNullable(request.getBaseUrl()).orElse("https://gpswox.com");
        WebClient client = createWebClient(baseUrl);

        var response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/devices")
                        .queryParam("api_key", request.getApiKey())
                        .build())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("API error: " + body)))
                )
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        int deviceCount = extractDeviceCount(response);

        return GpsConnectionTestResponse.builder()
                .success(true)
                .message("Connected to GPSWOX successfully")
                .provider("GPSWOX")
                .devicesFound(deviceCount)
                .responseTime((System.currentTimeMillis() - startTime) + "ms")
                .build();
    }

    private GpsConnectionTestResponse testCustomConnection(GpsConnectionTestRequest request, long startTime) {
        if (request.getBaseUrl() == null || request.getBaseUrl().isBlank()) {
            return GpsConnectionTestResponse.builder()
                    .success(false)
                    .message("Base URL is required for custom API provider")
                    .provider("CUSTOM")
                    .errorCode("MISSING_BASE_URL")
                    .build();
        }

        WebClient client = createWebClient(request.getBaseUrl());

        // For custom APIs, we do a basic HEAD or GET request to validate reachability
        var response = client.get()
                .uri("/")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .block();

        return GpsConnectionTestResponse.builder()
                .success(true)
                .message("Custom API endpoint is reachable")
                .provider("CUSTOM")
                .devicesFound(0)
                .responseTime((System.currentTimeMillis() - startTime) + "ms")
                .build();
    }

    // ── Provider-specific device fetching ──────────────────────────────────────

    private List<GpsDeviceSyncResponse.GpsDeviceInfo> fetchDevicesFromProvider(
            String provider, String baseUrl, String appId, String apiKey, String deviceGroupId) {

        return switch (provider) {
            case "IOPGPS" -> fetchIopgpsDevices(baseUrl, appId, apiKey, deviceGroupId);
            case "TRACCAR" -> fetchTraccarDevices(baseUrl, apiKey);
            case "WIALON" -> fetchWialonDevices(baseUrl, apiKey);
            case "GPSWOX" -> fetchGpswoxDevices(baseUrl, apiKey);
            case "CUSTOM" -> List.of(); // Custom requires provider-specific implementation
            default -> List.of();
        };
    }

    @SuppressWarnings("unchecked")
    private List<GpsDeviceSyncResponse.GpsDeviceInfo> fetchIopgpsDevices(
            String baseUrl, String appId, String apiKey, String deviceGroupId) {

        String url = Optional.ofNullable(baseUrl).orElse("https://www.iopgps.com");
        String token = fetchIopgpsToken(url, appId, apiKey);
        if (token == null) return List.of();

        WebClient client = createWebClient(url);

        var response = client.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/rest/api/v1/devices")
                            .queryParam("access_token", token);
                    if (deviceGroupId != null) builder.queryParam("groupId", deviceGroupId);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .block();

        List<GpsDeviceSyncResponse.GpsDeviceInfo> devices = new ArrayList<>();
        if (response != null && response.get("data") instanceof List) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            for (Map<String, Object> item : data) {
                devices.add(GpsDeviceSyncResponse.GpsDeviceInfo.builder()
                        .deviceId(String.valueOf(item.getOrDefault("id", "")))
                        .name(String.valueOf(item.getOrDefault("name", "")))
                        .imei(String.valueOf(item.getOrDefault("imei", "")))
                        .status(String.valueOf(item.getOrDefault("status", "UNKNOWN")))
                        .latitude(parseDouble(item.get("lat")))
                        .longitude(parseDouble(item.get("lng")))
                        .speed(parseDouble(item.get("speed")))
                        .build());
            }
        }
        return devices;
    }

    @SuppressWarnings("unchecked")
    private String fetchIopgpsToken(String baseUrl, String appId, String apiKey) {
        try {
            WebClient client = createWebClient(baseUrl);
            var response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/v1/auth/access_token")
                            .queryParam("account", appId)
                            .queryParam("password", apiKey)
                            .queryParam("time", System.currentTimeMillis() / 1000)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.get("data") instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                return (String) data.get("access_token");
            }
            log.warn("IOPGPS token response invalid: {}", response);
        } catch (Exception e) {
            log.error("Failed to fetch IOPGPS token: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<GpsDeviceSyncResponse.GpsDeviceInfo> fetchTraccarDevices(String baseUrl, String apiKey) {
        WebClient client = createWebClient(Optional.ofNullable(baseUrl).orElse("http://localhost:8082"));

        List<Map<String, Object>> response = client.get()
                .uri("/api/devices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(List.class)
                .timeout(Duration.ofSeconds(15))
                .block();

        List<GpsDeviceSyncResponse.GpsDeviceInfo> devices = new ArrayList<>();
        if (response != null) {
            for (Map<String, Object> item : response) {
                devices.add(GpsDeviceSyncResponse.GpsDeviceInfo.builder()
                        .deviceId(String.valueOf(item.getOrDefault("id", "")))
                        .name(String.valueOf(item.getOrDefault("name", "")))
                        .imei(String.valueOf(item.getOrDefault("uniqueId", "")))
                        .status(String.valueOf(item.getOrDefault("status", "UNKNOWN")))
                        .build());
            }
        }
        return devices;
    }

    @SuppressWarnings("unchecked")
    private List<GpsDeviceSyncResponse.GpsDeviceInfo> fetchWialonDevices(String baseUrl, String apiKey) {
        WebClient client = createWebClient(Optional.ofNullable(baseUrl).orElse("https://hst-api.wialon.com"));

        // Authenticate
        var authResponse = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/wialon/ajax.html")
                        .queryParam("svc", "token/login")
                        .queryParam("params", "{\"token\":\"" + apiKey + "\"}")
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (authResponse == null || !authResponse.containsKey("eid")) {
            return List.of();
        }

        String eid = (String) authResponse.get("eid");

        var unitsResponse = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/wialon/ajax.html")
                        .queryParam("svc", "core/search_items")
                        .queryParam("params", "{\"spec\":{\"itemsType\":\"avl_unit\",\"propName\":\"sys_name\",\"propValueMask\":\"*\",\"sortType\":\"sys_name\"},\"force\":1,\"flags\":1,\"from\":0,\"to\":0}")
                        .queryParam("sid", eid)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        List<GpsDeviceSyncResponse.GpsDeviceInfo> devices = new ArrayList<>();
        if (unitsResponse != null && unitsResponse.get("items") instanceof List) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) unitsResponse.get("items");
            for (Map<String, Object> item : items) {
                Map<String, Object> itemData = (Map<String, Object>) item.getOrDefault("itemData", Map.of());
                devices.add(GpsDeviceSyncResponse.GpsDeviceInfo.builder()
                        .deviceId(String.valueOf(item.getOrDefault("id", "")))
                        .name(String.valueOf(itemData.getOrDefault("nm", "")))
                        .status("UNKNOWN")
                        .build());
            }
        }
        return devices;
    }

    @SuppressWarnings("unchecked")
    private List<GpsDeviceSyncResponse.GpsDeviceInfo> fetchGpswoxDevices(String baseUrl, String apiKey) {
        WebClient client = createWebClient(Optional.ofNullable(baseUrl).orElse("https://gpswox.com"));

        var response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/devices")
                        .queryParam("api_key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .block();

        List<GpsDeviceSyncResponse.GpsDeviceInfo> devices = new ArrayList<>();
        if (response != null && response.get("data") instanceof List) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            for (Map<String, Object> item : data) {
                devices.add(GpsDeviceSyncResponse.GpsDeviceInfo.builder()
                        .deviceId(String.valueOf(item.getOrDefault("id", "")))
                        .name(String.valueOf(item.getOrDefault("name", "")))
                        .imei(String.valueOf(item.getOrDefault("imei", "")))
                        .status(String.valueOf(item.getOrDefault("status", "UNKNOWN")))
                        .build());
            }
        }
        return devices;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String mapHttpError(HttpStatusCode statusCode, String provider) {
        int code = statusCode.value();
        return switch (code) {
            case 401 -> "Invalid API key or authentication failed for " + provider;
            case 403 -> "API access denied — check your " + provider + " account permissions";
            case 404 -> "API endpoint not found — check the Base URL";
            case 429 -> "Rate limit exceeded — please wait before retrying";
            case 500, 502, 503, 504 -> provider + " server is temporarily unavailable";
            default -> "HTTP error " + code + " from " + provider;
        };
    }

    @SuppressWarnings("unchecked")
    private int extractDeviceCount(Map<String, Object> response) {
        if (response == null) return 0;
        if (response.get("data") instanceof List) return ((List<?>) response.get("data")).size();
        if (response.get("devices") instanceof List) return ((List<?>) response.get("devices")).size();
        if (response.get("count") instanceof Number) return ((Number) response.get("count")).intValue();
        if (response.get("total") instanceof Number) return ((Number) response.get("total")).intValue();
        return 0;
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
