package com.carrental.service;

import com.carrental.entity.GpsSettings;
import com.carrental.entity.TenantSettings;
import com.carrental.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemHealthServiceTest {
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TenantRepository tenantRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private GpsSettingsRepository gpsSettingsRepository;
    @Mock private TenantSettingsRepository tenantSettingsRepository;

    @TempDir
    Path storagePath;

    @Test
    void reportsMeasuredPlatformState() {
        SystemHealthService service = new SystemHealthService(
                jdbcTemplate,
                tenantRepository,
                contractRepository,
                paymentRepository,
                auditLogRepository,
                gpsSettingsRepository,
                tenantSettingsRepository);
        ReflectionTestUtils.setField(service, "storagePath", storagePath.toString());

        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(tenantRepository.count()).thenReturn(4L);
        when(contractRepository.count()).thenReturn(12L);
        when(paymentRepository.count()).thenReturn(20L);
        when(auditLogRepository.countByIsSuccessFalseAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        when(gpsSettingsRepository.findAll()).thenReturn(List.of(
                GpsSettings.builder().enabled(true).connectionStatus("CONNECTED").build()));
        when(tenantSettingsRepository.findAll()).thenReturn(List.of(
                TenantSettings.builder().smtpHost("smtp.example.com").build()));

        Map<String, Object> health = service.platformHealth();

        assertThat(health)
                .containsEntry("apiStatus", "healthy")
                .containsEntry("dbStatus", "healthy")
                .containsEntry("gpsStatus", "healthy")
                .containsEntry("emailStatus", "healthy")
                .containsEntry("agencyCount", 4L)
                .containsEntry("contractCount", 12L)
                .containsEntry("paymentCount", 20L)
                .containsEntry("errorsLast24Hours", 2L);
        assertThat(health.get("storageStatus")).isIn("healthy", "degraded");
        assertThat(health).containsKeys("storageDiskUsedPercent", "storageUsableBytes", "storageUsedBytes");
    }
}
