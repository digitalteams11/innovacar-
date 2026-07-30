package com.carrental.controller;

import com.carrental.entity.*;
import com.carrental.repository.ReportPreferencesRepository;
import com.carrental.repository.ReportRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.FeatureAccessService;
import com.carrental.service.reporting.ReportGenerationService;
import com.carrental.service.reporting.ReportPdfStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Covers the /api/reports/generate contract directly (spec sections 1-2, 9) —
 * the exact bug class this endpoint had: an existing period returned a bare
 * 400 with no errorCode/message. Every branch here must return structured
 * JSON with a precise status/errorCode, never an empty/generic 400.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportControllerTest {

    private static final Long TENANT_ID = 1L;

    @Mock private ReportRepository reportRepository;
    @Mock private ReportPreferencesRepository preferencesRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private FeatureAccessService featureAccessService;
    @Mock private ReportGenerationService reportGenerationService;
    @Mock private ReportPdfStorage pdfStorage;

    @InjectMocks private ReportController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        when(featureAccessService.isEnabledForCurrentTenant(anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void invalidReportType_returns400WithFieldErrors() {
        ResponseEntity<Map<String, Object>> response = controller.generateReport(Map.of("reportType", "WEEKLY"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("success", false).containsEntry("errorCode", "REPORT_TYPE_INVALID");
        assertThat(response.getBody().get("fieldErrors")).isNotNull();
    }

    @Test
    void monthOutOfRange_returns400WithFieldError() {
        ResponseEntity<Map<String, Object>> response = controller.generateReport(
                Map.of("reportType", "MONTHLY", "year", 2026, "month", 13));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("errorCode", "REPORT_PERIOD_INVALID");
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsKey("month");
    }

    @Test
    void yearWithoutMonth_returns400_requiresBothOrNeither() {
        ResponseEntity<Map<String, Object>> response = controller.generateReport(
                Map.of("reportType", "MONTHLY", "year", 2026));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("errorCode", "REPORT_PERIOD_INVALID");
    }

    @Test
    void emptyBody_defaultsToMonthlyAndDoesNotThrow() {
        when(reportGenerationService.generateManual(eq(TENANT_ID), eq(ReportType.MONTHLY), isNull(), isNull(),
                isNull(), eq(ReportGeneratedBy.MANUAL), eq(false)))
                .thenReturn(ReportGenerationService.GenerationOutcome.of(readyReport()));

        ResponseEntity<Map<String, Object>> response = controller.generateReport(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
    }

    @Test
    void duplicatePeriod_returns409WithReportAlreadyExistsAndExistingReportId() {
        Report existing = readyReport();
        when(reportGenerationService.generateManual(eq(TENANT_ID), eq(ReportType.MONTHLY), eq(2026), eq(6),
                isNull(), eq(ReportGeneratedBy.MANUAL), eq(false)))
                .thenReturn(ReportGenerationService.GenerationOutcome.skippedWithReport(
                        ReportGenerationService.SkipReason.ALREADY_EXISTS, existing));

        ResponseEntity<Map<String, Object>> response = controller.generateReport(
                Map.of("reportType", "MONTHLY", "year", 2026, "month", 6));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry("success", false).containsEntry("errorCode", "REPORT_ALREADY_EXISTS");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).containsEntry("reportId", 500L);
    }

    @Test
    void notEntitled_returns402WithReportingFeatureNotIncluded() {
        when(reportGenerationService.generateManual(eq(TENANT_ID), eq(ReportType.MONTHLY), isNull(), isNull(),
                isNull(), eq(ReportGeneratedBy.MANUAL), eq(false)))
                .thenReturn(ReportGenerationService.GenerationOutcome.skipped(ReportGenerationService.SkipReason.NOT_ENTITLED));

        ResponseEntity<Map<String, Object>> response = controller.generateReport(Map.of("reportType", "MONTHLY"));

        assertThat(response.getStatusCode().value()).isEqualTo(402);
        assertThat(response.getBody()).containsEntry("errorCode", "REPORTING_FEATURE_NOT_INCLUDED");
    }

    @Test
    void periodNotClosed_returns400WithReportPeriodInvalid() {
        when(reportGenerationService.generateManual(eq(TENANT_ID), eq(ReportType.MONTHLY), eq(2099), eq(1),
                isNull(), eq(ReportGeneratedBy.MANUAL), eq(false)))
                .thenReturn(ReportGenerationService.GenerationOutcome.skipped(ReportGenerationService.SkipReason.PERIOD_NOT_CLOSED));

        ResponseEntity<Map<String, Object>> response = controller.generateReport(
                Map.of("reportType", "MONTHLY", "year", 2099, "month", 1));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("errorCode", "REPORT_PERIOD_INVALID");
    }

    @Test
    void validYearlyRequest_neverSendsMonthToService() {
        when(reportGenerationService.generateManual(eq(TENANT_ID), eq(ReportType.YEARLY), eq(2025), isNull(),
                isNull(), eq(ReportGeneratedBy.MANUAL), eq(false)))
                .thenReturn(ReportGenerationService.GenerationOutcome.of(readyReport()));

        ResponseEntity<Map<String, Object>> response = controller.generateReport(
                Map.of("reportType", "YEARLY", "year", 2025, "month", 7));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
    }

    private Report readyReport() {
        return Report.builder().id(500L).reportType(ReportType.MONTHLY).status(ReportStatus.GENERATED)
                .periodStart(LocalDateTime.of(2026, 6, 1, 0, 0)).periodEnd(LocalDateTime.of(2026, 7, 1, 0, 0))
                .calculationVersion(1).emailStatus(ReportEmailStatus.NOT_SENT).build();
    }
}
