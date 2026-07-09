package com.carrental.service;

import com.carrental.entity.GpsSettings;
import com.carrental.entity.TenantSettings;
import com.carrental.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemHealthService {
    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final ContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogRepository auditLogRepository;
    private final GpsSettingsRepository gpsSettingsRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    @Value("${app.storage.path:storage}")
    private String storagePath;

    public Map<String, Object> platformHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(databaseHealth());
        result.putAll(integrationHealth());
        result.putAll(storageHealth());
        result.put("apiStatus", "healthy");
        result.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        result.put("agencyCount", tenantRepository.count());
        result.put("contractCount", contractRepository.count());
        result.put("paymentCount", paymentRepository.count());
        result.put("errorsLast24Hours",
                auditLogRepository.countByIsSuccessFalseAndCreatedAtAfter(LocalDateTime.now().minusHours(24)));
        result.put("timestamp", LocalDateTime.now());
        return result;
    }

    private Map<String, Object> databaseHealth() {
        long startedAt = System.nanoTime();
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (value == null || value != 1) throw new IllegalStateException("Unexpected database probe result");
            return Map.of(
                    "dbStatus", "healthy",
                    "dbLatencyMs", elapsedMillis(startedAt));
        } catch (RuntimeException ex) {
            log.error("Platform database health probe failed", ex);
            return Map.of(
                    "dbStatus", "down",
                    "dbLatencyMs", elapsedMillis(startedAt));
        }
    }

    private Map<String, Object> integrationHealth() {
        List<GpsSettings> gpsSettings = gpsSettingsRepository.findAll();
        String gpsStatus;
        if (gpsSettings.isEmpty() || gpsSettings.stream().noneMatch(item -> Boolean.TRUE.equals(item.getEnabled()))) {
            gpsStatus = "not_configured";
        } else if (gpsSettings.stream().anyMatch(item -> "ERROR".equalsIgnoreCase(item.getConnectionStatus()))) {
            gpsStatus = "degraded";
        } else if (gpsSettings.stream().anyMatch(item -> "CONNECTED".equalsIgnoreCase(item.getConnectionStatus()))) {
            gpsStatus = "healthy";
        } else {
            gpsStatus = "degraded";
        }

        List<TenantSettings> tenantSettings = tenantSettingsRepository.findAll();
        long configuredEmailTenants = tenantSettings.stream()
                .filter(item -> item.getSmtpHost() != null && !item.getSmtpHost().isBlank())
                .count();
        String emailStatus = configuredEmailTenants == 0 ? "not_configured" : "healthy";

        return Map.of(
                "gpsStatus", gpsStatus,
                "gpsConfiguredTenants", gpsSettings.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count(),
                "emailStatus", emailStatus,
                "emailConfiguredTenants", configuredEmailTenants);
    }

    private Map<String, Object> storageHealth() {
        try {
            Path path = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(path);
            FileStore fileStore = Files.getFileStore(path);
            long applicationBytes;
            try (Stream<Path> files = Files.walk(path)) {
                applicationBytes = files.filter(Files::isRegularFile).mapToLong(this::fileSize).sum();
            }
            long totalBytes = fileStore.getTotalSpace();
            long usableBytes = fileStore.getUsableSpace();
            double usedPercent = totalBytes == 0 ? 0 : ((double) (totalBytes - usableBytes) / totalBytes) * 100;
            return Map.of(
                    "storageStatus", usedPercent >= 95 ? "down" : usedPercent >= 85 ? "degraded" : "healthy",
                    "storagePath", path.toString(),
                    "storageUsedBytes", applicationBytes,
                    "storageDiskUsedPercent", Math.round(usedPercent * 10.0) / 10.0,
                    "storageUsableBytes", usableBytes);
        } catch (IOException | RuntimeException ex) {
            log.error("Platform storage health probe failed", ex);
            return Map.of(
                    "storageStatus", "down",
                    "storageUsedBytes", 0L,
                    "storageDiskUsedPercent", 0.0,
                    "storageUsableBytes", 0L);
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            log.warn("Unable to read storage file size for {}", path);
            return 0L;
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
