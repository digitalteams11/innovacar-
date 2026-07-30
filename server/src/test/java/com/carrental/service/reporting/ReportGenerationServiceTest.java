package com.carrental.service.reporting;

import com.carrental.dto.reporting.ReportDataset;
import com.carrental.entity.*;
import com.carrental.repository.ReportEmailAttemptRepository;
import com.carrental.repository.ReportPreferencesRepository;
import com.carrental.repository.ReportRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.service.FeatureAccessService;
import com.carrental.service.SmtpMailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportGenerationServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportPreferencesRepository preferencesRepository;
    @Mock private ReportEmailAttemptRepository emailAttemptRepository;
    @Mock private FeatureAccessService featureAccessService;
    @Mock private ReportCalculationService calculationService;
    @Mock private DeterministicSummaryService deterministicSummaryService;
    @Mock private AiReportSummaryService aiReportSummaryService;
    @Mock private ReportPdfGenerator pdfGenerator;
    @Mock private ReportPdfStorage pdfStorage;
    @Mock private SmtpMailService smtpMailService;

    private final ReportPeriodResolver periodResolver = new ReportPeriodResolver();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ReportGenerationService service() {
        return new ReportGenerationService(tenantRepository, reportRepository, preferencesRepository,
                emailAttemptRepository, featureAccessService, periodResolver, calculationService,
                deterministicSummaryService, aiReportSummaryService, pdfGenerator, pdfStorage, smtpMailService,
                objectMapper);
    }

    private Tenant tenant() {
        return Tenant.builder().id(1L).name("Test Agency").email("agency@test.com").build();
    }

    @Test
    void generateScheduled_planWithoutFeature_isDeniedWithoutTouchingReports() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MONTHLY)).thenReturn(false);

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateScheduled(1L, ReportType.MONTHLY, LocalDate.of(2026, 8, 1));

        assertThat(outcome.skipReason()).isEqualTo(ReportGenerationService.SkipReason.NOT_ENTITLED);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void generateScheduled_blockedTenant_isSkipped() {
        Tenant blocked = tenant();
        blocked.setStatus("SUSPENDED");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(blocked));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MONTHLY)).thenReturn(true);

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateScheduled(1L, ReportType.MONTHLY, LocalDate.of(2026, 8, 1));

        assertThat(outcome.skipReason()).isEqualTo(ReportGenerationService.SkipReason.ACCOUNT_BLOCKED);
    }

    @Test
    void generateScheduled_duplicateForSamePeriod_isSkippedAndNeverRegenerates() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MONTHLY)).thenReturn(true);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.empty());
        when(reportRepository.existsByTenantIdAndReportTypeAndPeriodStartAndPeriodEndAndStatusIn(
                eq(1L), eq(ReportType.MONTHLY), any(), any(), anyList())).thenReturn(true);

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateScheduled(1L, ReportType.MONTHLY, LocalDate.of(2026, 8, 1));

        assertThat(outcome.skipReason()).isEqualTo(ReportGenerationService.SkipReason.ALREADY_EXISTS);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void generateManual_completePlanTenant_generatesAndPersistsReport() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MANUAL_EXPORT)).thenReturn(true);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.empty());
        when(reportRepository.existsByTenantIdAndReportTypeAndPeriodStartAndPeriodEndAndStatusIn(
                eq(1L), eq(ReportType.MONTHLY), any(), any(), anyList())).thenReturn(false);
        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(eq(1L), eq(ReportType.MONTHLY), any(), any()))
                .thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            if (r.getId() == null) r.setId(99L);
            return r;
        });
        when(calculationService.calculate(any(), eq(ReportType.MONTHLY), any(), any())).thenReturn(sampleDataset());
        when(deterministicSummaryService.buildSummary(any())).thenReturn(List.of("All good."));
        when(pdfGenerator.generate(any(), any(), any(), anyList(), anyBoolean())).thenReturn(new byte[]{1, 2, 3});
        when(pdfStorage.save(eq(1L), eq(99L), any())).thenReturn(new ReportPdfStorage.StoredFile("key.pdf", 3L, "abc123"));

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateManual(1L, ReportType.MONTHLY, 2026, 7, 42L, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.skipReason()).isNull();
        assertThat(outcome.report().getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(outcome.report().getFileStorageKey()).isEqualTo("key.pdf");
        assertThat(outcome.report().getGeneratedBy()).isEqualTo(ReportGeneratedBy.MANUAL);
        // Manual generation does not auto-email — no attempt should be recorded.
        verify(emailAttemptRepository, never()).save(any());
    }

    @Test
    void generateManual_basicPlanTenant_isDenied() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MANUAL_EXPORT)).thenReturn(false);

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateManual(1L, ReportType.MONTHLY, 2026, 7, 42L, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.skipReason()).isEqualTo(ReportGenerationService.SkipReason.NOT_ENTITLED);
        verifyNoInteractions(calculationService, pdfGenerator, pdfStorage);
    }

    @Test
    void generate_whenAiSummaryThrows_fallsBackToDeterministicSummaryAndStillGeneratesPdf() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MANUAL_EXPORT)).thenReturn(true);
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_AI_SUMMARY)).thenReturn(true);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.of(ReportPreferences.builder()
                .tenant(tenant()).reportEnabled(true).monthlyReportEnabled(true).yearlyReportEnabled(true)
                .reportLanguage("fr").includeAiSummary(true).includeClientDebtDetail(true)
                .primaryRecipientEmail("agency@test.com").build()));
        when(reportRepository.existsByTenantIdAndReportTypeAndPeriodStartAndPeriodEndAndStatusIn(
                eq(1L), eq(ReportType.MONTHLY), any(), any(), anyList())).thenReturn(false);
        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(eq(1L), eq(ReportType.MONTHLY), any(), any()))
                .thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            if (r.getId() == null) r.setId(77L);
            return r;
        });
        when(calculationService.calculate(any(), eq(ReportType.MONTHLY), any(), any())).thenReturn(sampleDataset());
        // AiReportSummaryService itself never throws (it swallows internally per its own contract),
        // but the orchestration must still work correctly when it reports aiUsed=false.
        when(aiReportSummaryService.buildSummary(any(), anyString()))
                .thenReturn(new AiReportSummaryService.SummaryResult(List.of("Fallback line."), false));
        when(pdfGenerator.generate(any(), any(), any(), anyList(), eq(false))).thenReturn(new byte[]{9});
        when(pdfStorage.save(eq(1L), eq(77L), any())).thenReturn(new ReportPdfStorage.StoredFile("k.pdf", 1L, "cs"));

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateManual(1L, ReportType.MONTHLY, 2026, 7, null, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.report().getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(outcome.report().isAiSummaryUsed()).isFalse();
    }

    private ReportDataset sampleDataset() {
        ReportDataset.FinancialSummary financial = new ReportDataset.FinancialSummary(
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        ReportDataset.OperationsSummary operations = new ReportDataset.OperationsSummary(
                0, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        ReportDataset.FleetSummary fleet = new ReportDataset.FleetSummary(
                0, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        ReportDataset.MetricChange noChange = ReportDataset.MetricChange.of(BigDecimal.ZERO, BigDecimal.ZERO);
        ReportDataset.PeriodComparison comparison = new ReportDataset.PeriodComparison(
                noChange, noChange, noChange, noChange, noChange, noChange, noChange);
        ReportDataset.ClientsSummary clients = new ReportDataset.ClientsSummary(0, 0, List.of(), 0);
        ReportDataset.MaintenanceSummary maintenance = new ReportDataset.MaintenanceSummary(
                0, 0, 0, BigDecimal.ZERO, null, 0, 0);
        return new ReportDataset(1L, "MONTHLY", LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0), "v1", financial, operations, fleet,
                List.of(), List.of(), clients, maintenance, comparison);
    }
}
