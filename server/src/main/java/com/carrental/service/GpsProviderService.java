package com.carrental.service;

import com.carrental.dto.gps.*;
import com.carrental.entity.GpsDevice;
import com.carrental.entity.GpsSettings;
import com.carrental.entity.Vehicle;
import com.carrental.repository.GpsDeviceRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import com.carrental.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GpsProviderService {

    /** IOPGPS only ever has one valid API host — the docs/dashboard URLs are not the API. */
    public static final String IOPGPS_BASE_URL = "https://open.iopgps.com";

    /** IOPGPS access tokens are valid ~2h; we refresh a bit early to avoid using a stale one mid-request. */
    private static final long IOPGPS_TOKEN_CACHE_TTL_SECONDS = 90 * 60;

    private final EncryptionUtil encryptionUtil;
    private final VehicleRepository vehicleRepository;
    private final GpsDeviceRepository gpsDeviceRepository;

    private final Map<String, CachedIopgpsToken> iopgpsTokenCache = new ConcurrentHashMap<>();

    private record CachedIopgpsToken(String token, long expiresAtEpochSecond) {
        boolean isValid() {
            return Instant.now().getEpochSecond() < expiresAtEpochSecond;
        }
    }

    private WebClient createWebClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .build();
    }

    // ── Basic Auth helper ──────────────────────────────────────────────────────

    private String basicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    // ── Connection test (raw credentials, nothing saved) ─────────────────────

    public GpsConnectionTestResponse testConnection(GpsConnectionTestRequest request) {
        long startTime = System.currentTimeMillis();
        String provider = request.getProvider().toUpperCase();

        try {
            return switch (provider) {
                case "IOPGPS"  -> testIopgpsConnection(request, startTime);
                case "TRACCAR" -> testTraccarConnection(request, startTime);
                case "WIALON"  -> testWialonConnection(request, startTime);
                case "GPSWOX"  -> testGpswoxConnection(request, startTime);
                case "CUSTOM"  -> testCustomConnection(request, startTime);
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

    public GpsConnectionTestResponse testConnectionWithStoredSettings(GpsSettings settings) {
        boolean hasApiKey  = settings != null && settings.getApiKeyEncrypted() != null
                && !settings.getApiKeyEncrypted().isBlank();
        boolean hasPassword = settings != null && settings.getEncryptedPassword() != null
                && !settings.getEncryptedPassword().isBlank();

        if (!hasApiKey && !hasPassword) {
            return GpsConnectionTestResponse.builder()
                    .success(false)
                    .message("No GPS credentials configured")
                    .errorCode("NO_CREDENTIALS")
                    .build();
        }

        GpsConnectionTestRequest request = new GpsConnectionTestRequest();
        request.setProvider(settings.getProvider());
        request.setAppId(settings.getAppId());
        request.setBaseUrl(settings.getBaseUrl());
        request.setDeviceGroupId(settings.getDeviceGroupId());
        if (hasApiKey)  request.setApiKey(encryptionUtil.decrypt(settings.getApiKeyEncrypted()));
        if (hasPassword) request.setPassword(encryptionUtil.decrypt(settings.getEncryptedPassword()));

        return testConnection(request);
    }

    // ── Device sync ────────────────────────────────────────────────────────────

    @Transactional
    public GpsDeviceSyncResponse syncDevices(GpsSettings settings) {
        Long tenantId = TenantContext.getCurrentTenantId();

        boolean hasApiKey  = settings != null && settings.getApiKeyEncrypted() != null
                && !settings.getApiKeyEncrypted().isBlank();
        boolean hasPassword = settings != null && settings.getEncryptedPassword() != null
                && !settings.getEncryptedPassword().isBlank();

        if (!hasApiKey && !hasPassword) {
            return GpsDeviceSyncResponse.builder()
                    .success(false)
                    .message("No GPS credentials configured")
                    .build();
        }

        String provider  = settings.getProvider().toUpperCase();
        String apiKey    = hasApiKey  ? encryptionUtil.decrypt(settings.getApiKeyEncrypted())  : null;
        String password  = hasPassword ? encryptionUtil.decrypt(settings.getEncryptedPassword()) : null;

        try {
            List<GpsDeviceSyncResponse.GpsDeviceInfo> providerDevices = fetchDevicesFromProvider(
                    provider,
                    settings.getBaseUrl(),
                    settings.getAppId(),
                    apiKey,
                    password,
                    settings.getDeviceGroupId(),
                    settings.getAuthHeaderName(),
                    settings.getAuthPrefix()
            );

            // Build lookup maps for matching vehicles
            List<Vehicle> vehicles = vehicleRepository.findAllByTenantId(tenantId);
            Map<String, Vehicle> byGpsDeviceId = new HashMap<>();
            Map<String, Vehicle> byImei        = new HashMap<>();
            Map<String, Vehicle> byPlate       = new HashMap<>();
            for (Vehicle v : vehicles) {
                if (v.getGpsDeviceId() != null && !v.getGpsDeviceId().isBlank())
                    byGpsDeviceId.put(v.getGpsDeviceId().trim().toLowerCase(), v);
                if (v.getGpsImei() != null && !v.getGpsImei().isBlank())
                    byImei.put(v.getGpsImei().trim().toLowerCase(), v);
                if (v.getPlate() != null && !v.getPlate().isBlank())
                    byPlate.put(v.getPlate().trim().toLowerCase(), v);
            }

            // Build lookup map for existing gps_device rows
            List<GpsDevice> existingDevices = gpsDeviceRepository.findAllByTenantId(tenantId);
            Map<String, GpsDevice> byProviderDeviceId = existingDevices.stream()
                    .collect(Collectors.toMap(GpsDevice::getProviderDeviceId, d -> d));

            LocalDateTime now = LocalDateTime.now();
            int created = 0;
            int updated = 0;
            int matched = 0;

            List<GpsDevice> toSave = new ArrayList<>();
            List<Vehicle>   vehiclesToSave = new ArrayList<>();
            List<GpsDeviceSyncResponse.GpsDeviceInfo> enrichedInfos = new ArrayList<>();

            for (GpsDeviceSyncResponse.GpsDeviceInfo info : providerDevices) {
                String devId = info.getDeviceId();
                GpsDevice gd = byProviderDeviceId.get(devId);
                boolean isNew = gd == null;

                if (isNew) {
                    gd = GpsDevice.builder()
                            .tenant(settings.getTenant())
                            .providerDeviceId(devId)
                            .status("UNKNOWN")
                            .ignition(false)
                            .build();
                    created++;
                } else {
                    updated++;
                }

                // Update fields from provider
                gd.setName(info.getName());
                gd.setImei(info.getImei());
                gd.setPlateNumber(info.getPlateNumber());
                gd.setStatus(normalizeStatus(info.getStatus()));
                gd.setLatitude(info.getLatitude());
                gd.setLongitude(info.getLongitude());
                gd.setSpeed(info.getSpeed());
                gd.setLastSyncedAt(now);

                // Try to match a vehicle
                Vehicle matchedVehicle = null;
                if (devId != null)
                    matchedVehicle = byGpsDeviceId.get(devId.trim().toLowerCase());
                if (matchedVehicle == null && info.getImei() != null && !info.getImei().isBlank())
                    matchedVehicle = byImei.get(info.getImei().trim().toLowerCase());
                if (matchedVehicle == null && info.getPlateNumber() != null && !info.getPlateNumber().isBlank())
                    matchedVehicle = byPlate.get(info.getPlateNumber().trim().toLowerCase());

                Long linkedVehicleId = null;
                if (matchedVehicle != null) {
                    linkedVehicleId = matchedVehicle.getId();
                    gd.setVehicleId(linkedVehicleId);
                    // Update vehicle GPS data
                    matchedVehicle.setGpsEnabled(true);
                    matchedVehicle.setGpsImei(info.getImei());
                    if (info.getLatitude()  != null) matchedVehicle.setLastLatitude(info.getLatitude());
                    if (info.getLongitude() != null) matchedVehicle.setLastLongitude(info.getLongitude());
                    if (info.getSpeed()     != null) matchedVehicle.setLastSpeed(info.getSpeed());
                    matchedVehicle.setLastGpsUpdate(now);
                    vehiclesToSave.add(matchedVehicle);
                    matched++;
                }

                toSave.add(gd);
                enrichedInfos.add(GpsDeviceSyncResponse.GpsDeviceInfo.builder()
                        .deviceId(devId)
                        .name(info.getName())
                        .imei(info.getImei())
                        .plateNumber(info.getPlateNumber())
                        .status(normalizeStatus(info.getStatus()))
                        .latitude(info.getLatitude())
                        .longitude(info.getLongitude())
                        .speed(info.getSpeed())
                        .linkedVehicleId(linkedVehicleId)
                        .build());
            }

            gpsDeviceRepository.saveAll(toSave);
            if (!vehiclesToSave.isEmpty()) vehicleRepository.saveAll(vehiclesToSave);

            settings.setLastSyncAt(now);
            settings.setActiveDevices(providerDevices.size());

            int unmatched = providerDevices.size() - matched;
            return GpsDeviceSyncResponse.builder()
                    .success(true)
                    .message("Devices synchronized successfully")
                    .devicesSynced(providerDevices.size())
                    .devicesCreated(created)
                    .devicesUpdated(updated)
                    .matchedVehicles(matched)
                    .unmatchedDevices(unmatched)
                    .devices(enrichedInfos)
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
        String appId = request.getAppId();
        String secretKey = request.getApiKey();

        try {
            String token = getOrFetchIopgpsToken(appId, secretKey);
            if (token == null) {
                boolean looksLikeEmail = appId != null && appId.contains("@");
                if (looksLikeEmail) {
                    return GpsConnectionTestResponse.builder()
                            .success(false)
                            .message("The IOPGPS account name is incorrect.")
                            .provider("IOPGPS")
                            .errorCode("IOPGPS_INVALID_APP_ID")
                            .action("Copy the APPID/account name shown in IOPGPS API settings.")
                            .responseTime((System.currentTimeMillis() - startTime) + "ms")
                            .build();
                }
                return GpsConnectionTestResponse.builder()
                        .success(false)
                        .message("IOPGPS authentication failed")
                        .provider("IOPGPS")
                        .errorCode("AUTH_FAILED")
                        .responseTime((System.currentTimeMillis() - startTime) + "ms")
                        .build();
            }

            WebClient client = createWebClient(IOPGPS_BASE_URL);
            var response = client.get()
                    .uri("/rest/api/v1/devices")
                    .header("accessToken", token)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, cr ->
                            cr.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException("API error: " + body)))
                    )
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return GpsConnectionTestResponse.builder()
                    .success(true)
                    .message("Connected to IOPGPS successfully")
                    .provider("IOPGPS")
                    .devicesFound(extractDeviceCount(response))
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

        boolean useBasicAuth = request.getPassword() != null && !request.getPassword().isBlank();
        String username = request.getAppId(); // appId doubles as Traccar username

        var spec = client.get()
                .uri("/api/devices")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (useBasicAuth && username != null && !username.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, basicAuthHeader(username, request.getPassword()));
        } else if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getApiKey());
        } else {
            return GpsConnectionTestResponse.builder()
                    .success(false)
                    .message("Traccar requires either an API token or username + password")
                    .provider("TRACCAR")
                    .errorCode("MISSING_CREDENTIALS")
                    .responseTime((System.currentTimeMillis() - startTime) + "ms")
                    .build();
        }

        var response = spec
                .retrieve()
                .onStatus(HttpStatusCode::isError, cr ->
                        cr.bodyToMono(String.class)
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

        if (authResponse == null || !authResponse.containsKey("eid")) {
            return GpsConnectionTestResponse.builder()
                    .success(false)
                    .message("Wialon authentication failed")
                    .provider("WIALON")
                    .errorCode("AUTH_FAILED")
                    .responseTime((System.currentTimeMillis() - startTime) + "ms")
                    .build();
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

        int count = 0;
        if (unitsResponse != null && unitsResponse.get("items") instanceof List<?> items) {
            count = items.size();
        }

        return GpsConnectionTestResponse.builder()
                .success(true)
                .message("Connected to Wialon successfully")
                .provider("WIALON")
                .devicesFound(count)
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
                .onStatus(HttpStatusCode::isError, cr ->
                        cr.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("API error: " + body)))
                )
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        return GpsConnectionTestResponse.builder()
                .success(true)
                .message("Connected to GPSWOX successfully")
                .provider("GPSWOX")
                .devicesFound(extractDeviceCount(response))
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
            String provider,
            String baseUrl,
            String appId,
            String apiKey,
            String password,
            String deviceGroupId,
            String authHeaderName,
            String authPrefix) {

        return switch (provider) {
            case "IOPGPS"  -> fetchIopgpsDevices(appId, apiKey, deviceGroupId);
            case "TRACCAR" -> fetchTraccarDevices(baseUrl, appId, apiKey, password);
            case "WIALON"  -> fetchWialonDevices(baseUrl, apiKey);
            case "GPSWOX"  -> fetchGpswoxDevices(baseUrl, apiKey);
            default        -> List.of();
        };
    }

    @SuppressWarnings("unchecked")
    private List<GpsDeviceSyncResponse.GpsDeviceInfo> fetchIopgpsDevices(
            String appId, String apiKey, String deviceGroupId) {

        String token = getOrFetchIopgpsToken(appId, apiKey);
        if (token == null) return List.of();

        WebClient client = createWebClient(IOPGPS_BASE_URL);
        var response = client.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/rest/api/v1/devices");
                    if (deviceGroupId != null) builder.queryParam("groupId", deviceGroupId);
                    return builder.build();
                })
                .header("accessToken", token)
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
    private List<GpsDeviceSyncResponse.GpsDeviceInfo> fetchTraccarDevices(
            String baseUrl, String username, String apiKey, String password) {

        WebClient client = createWebClient(Optional.ofNullable(baseUrl).orElse("http://localhost:8082"));

        boolean useBasicAuth = password != null && !password.isBlank()
                && username != null && !username.isBlank();

        var spec = client.get()
                .uri("/api/devices")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (useBasicAuth) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, basicAuthHeader(username, password));
        } else if (apiKey != null && !apiKey.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        List<Map<String, Object>> response = spec
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
                        .latitude(parseDouble(item.get("latitude")))
                        .longitude(parseDouble(item.get("longitude")))
                        .speed(parseDouble(item.get("speed")))
                        .build());
            }
        }
        return devices;
    }

    @SuppressWarnings("unchecked")
    private List<GpsDeviceSyncResponse.GpsDeviceInfo> fetchWialonDevices(String baseUrl, String apiKey) {
        WebClient client = createWebClient(Optional.ofNullable(baseUrl).orElse("https://hst-api.wialon.com"));

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

        if (authResponse == null || !authResponse.containsKey("eid")) return List.of();

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

    /** Returns a cached IOPGPS access token if still fresh, otherwise authenticates and caches the result. */
    private String getOrFetchIopgpsToken(String appId, String secretKey) {
        if (appId == null || appId.isBlank() || secretKey == null || secretKey.isBlank()) {
            return null;
        }
        String cacheKey = appId.trim();
        CachedIopgpsToken cached = iopgpsTokenCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.token();
        }

        String token = fetchIopgpsToken(appId, secretKey);
        if (token != null) {
            long expiresAt = Instant.now().getEpochSecond() + IOPGPS_TOKEN_CACHE_TTL_SECONDS;
            iopgpsTokenCache.put(cacheKey, new CachedIopgpsToken(token, expiresAt));
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    private String fetchIopgpsToken(String appId, String secretKey) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = buildSignature(secretKey, timestamp);

            Map<String, Object> body = new HashMap<>();
            body.put("appid", appId);
            body.put("time", timestamp);
            body.put("signature", signature);

            WebClient client = createWebClient(IOPGPS_BASE_URL);
            var response = client.post()
                    .uri("/api/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, cr ->
                            cr.bodyToMono(String.class)
                                    .flatMap(respBody -> Mono.error(new RuntimeException("IOPGPS auth error: " + respBody)))
                    )
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            String token = extractIopgpsAccessToken(response);
            if (token == null) {
                log.warn("IOPGPS auth response missing accessToken: {}", response);
            }
            return token;
        } catch (Exception e) {
            log.error("Failed to fetch IOPGPS token: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractIopgpsAccessToken(Map<String, Object> response) {
        if (response == null) return null;
        Object direct = response.get("accessToken");
        if (direct instanceof String s && !s.isBlank()) return s;
        if (response.get("data") instanceof Map<?, ?> data) {
            Object token = data.get("accessToken");
            if (token instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    /**
     * IOPGPS signature scheme: md5(md5(secretKey) + time), both hashes lowercase 32-char hex.
     * secretKey is the API key generated in IOPGPS API Configuration — not the login password.
     */
    private String buildSignature(String secretKey, long unixTimestamp) {
        String firstHash = md5Lowercase(secretKey);
        return md5Lowercase(firstHash + unixTimestamp);
    }

    private String md5Lowercase(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String normalizeStatus(String providerStatus) {
        if (providerStatus == null) return "UNKNOWN";
        return switch (providerStatus.toUpperCase()) {
            case "ONLINE", "ACTIVE", "CONNECTED"                     -> "ONLINE";
            case "OFFLINE", "INACTIVE", "DISCONNECTED", "NOTACTIVE"  -> "OFFLINE";
            case "MOVING", "INMOTION", "MOTION"                      -> "MOVING";
            case "STOPPED", "IDLE", "PARKED", "STATIONARY"           -> "STOPPED";
            default -> "UNKNOWN";
        };
    }

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
        if (response.get("data") instanceof List)    return ((List<?>) response.get("data")).size();
        if (response.get("devices") instanceof List) return ((List<?>) response.get("devices")).size();
        if (response.get("count") instanceof Number) return ((Number) response.get("count")).intValue();
        if (response.get("total") instanceof Number) return ((Number) response.get("total")).intValue();
        return 0;
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
