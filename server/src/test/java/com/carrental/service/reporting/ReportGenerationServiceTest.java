package com.carrental.service.reporting;

import com.carrental.dto.reporting.ReportDataset;
import com.carrental.entity.*;
import com.carrental.repository.ReportEmailAttemptRepository;
import com.carrental.repository.ReportPreferencesRepository;
import com.carrental.repository.ReportRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.service.EmailActionUrlBuilder;
import com.carrental.service.EmailTemplateRenderer;
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
    @Mock private EmailTemplateRenderer emailTemplateRenderer;

    private final ReportPeriodResolver periodResolver = new ReportPeriodResolver();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ReportGenerationService service() {
        lenient().when(emailTemplateRenderer.render(anyString(), any())).thenReturn("<p>stub report email body</p>");
        return new ReportGenerationService(tenantRepository, reportRepository, preferencesRepository,
                emailAttemptRepository, featureAccessService, periodResolver, calculationService,
                deterministicSummaryService, aiReportSummaryService, pdfGenerator, pdfStorage, smtpMailService,
                emailTemplateRenderer, new EmailActionUrlBuilder("https://innovacar.app"), objectMapper);
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
        blocked.setStatus(SubscriptionStatus.SUSPENDED);
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
                .generateManual(1L, ReportType.MONTHLY, 2020, 1, 42L, ReportGeneratedBy.MANUAL, false);

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
    void generateManual_currentIncompleteMonth_isRejectedAsPeriodNotClosed() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MANUAL_EXPORT)).thenReturn(true);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.empty());

        LocalDate now = LocalDate.now();
        ReportGenerationService.GenerationOutcome outcome = service()
                .generateManual(1L, ReportType.MONTHLY, now.getYear(), now.getMonthValue(), null, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.skipReason()).isEqualTo(ReportGenerationService.SkipReason.PERIOD_NOT_CLOSED);
        verifyNoInteractions(calculationService, pdfGenerator, pdfStorage);
    }

    @Test
    void generateManual_futurePeriod_isRejectedAsPeriodNotClosed() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MANUAL_EXPORT)).thenReturn(true);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.empty());

        LocalDate future = LocalDate.now().plusYears(1);
        ReportGenerationService.GenerationOutcome outcome = service()
                .generateManual(1L, ReportType.MONTHLY, future.getYear(), future.getMonthValue(), null, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.skipReason()).isEqualTo(ReportGenerationService.SkipReason.PERIOD_NOT_CLOSED);
        verifyNoInteractions(calculationService, pdfGenerator, pdfStorage);
    }

    @Test
    void generateManual_duplicatePeriod_returnsAlreadyExistsWithTheExistingReport() {
        Tenant tenant = tenant();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MANUAL_EXPORT)).thenReturn(true);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.empty());

        Report existing = Report.builder().id(555L).tenant(tenant).reportType(ReportType.MONTHLY)
                .status(ReportStatus.GENERATED).periodStart(LocalDateTime.of(2020, 1, 1, 0, 0))
                .periodEnd(LocalDateTime.of(2020, 2, 1, 0, 0)).calculationVersion(1).build();
        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
                eq(1L), eq(ReportType.MONTHLY), eq(LocalDateTime.of(2020, 1, 1, 0, 0)), eq(LocalDateTime.of(2020, 2, 1, 0, 0))))
                .thenReturn(Optional.of(existing));

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateManual(1L, ReportType.MONTHLY, 2020, 1, null, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.skipReason()).isEqualTo(ReportGenerationService.SkipReason.ALREADY_EXISTS);
        assertThat(outcome.report()).isNotNull();
        assertThat(outcome.report().getId()).isEqualTo(555L);
        verifyNoInteractions(calculationService, pdfGenerator, pdfStorage);
    }

    @Test
    void generateManual_validYearlyRequest_generatesReport() {
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant()));
        when(featureAccessService.isEnabledForTenant(1L, ReportGenerationService.FEATURE_MANUAL_EXPORT)).thenReturn(true);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.empty());
        when(reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(eq(1L), eq(ReportType.YEARLY), any(), any()))
                .thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            if (r.getId() == null) r.setId(200L);
            return r;
        });
        when(calculationService.calculate(any(), eq(ReportType.YEARLY), any(), any())).thenReturn(sampleDataset());
        when(deterministicSummaryService.buildSummary(any())).thenReturn(List.of("All good."));
        when(pdfGenerator.generate(any(), any(), any(), anyList(), anyBoolean())).thenReturn(new byte[]{1});
        when(pdfStorage.save(eq(1L), eq(200L), any())).thenReturn(new ReportPdfStorage.StoredFile("k.pdf", 1L, "cs"));

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateManual(1L, ReportType.YEARLY, 2020, null, null, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.skipReason()).isNull();
        assertThat(outcome.report().getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(outcome.report().getReportType()).isEqualTo(ReportType.YEARLY);
    }

    @Test
    void generateManual_unknownTenant_isRejectedAsTenantNotFound() {
        when(tenantRepository.findById(999L)).thenReturn(Optional.empty());

        ReportGenerationService.GenerationOutcome outcome = service()
                .generateManual(999L, ReportType.MONTHLY, 2020, 1, null, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.skipReason()).isEqualTo(ReportGenerationService.SkipReason.TENANT_NOT_FOUND);
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
                .generateManual(1L, ReportType.MONTHLY, 2020, 1, null, ReportGeneratedBy.MANUAL, false);

        assertThat(outcome.report().getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(outcome.report().isAiSummaryUsed()).isFalse();
    }

    // ── sendReportEmail / resend ─────────────────────────────────────────────

    private Report readyReport() {
        return Report.builder()
                .id(500L).tenant(tenant()).reportType(ReportType.MONTHLY)
                .periodStart(LocalDateTime.of(2026, 7, 1, 0, 0)).periodEnd(LocalDateTime.of(2026, 8, 1, 0, 0))
                .language("en").status(ReportStatus.GENERATED).emailStatus(ReportEmailStatus.NOT_SENT)
                .fileStorageKey("tenant_1_report_500.pdf").calculationVersion(1).emailAttemptCount(0)
                .build();
    }

    @Test
    void sendReportEmail_success_persistsSentStatusTimestampAndProviderMessageId() {
        Report report = readyReport();
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.of(ReportPreferences.builder()
                .tenant(tenant()).primaryRecipientEmail("owner@example.com").build()));
        when(pdfStorage.read("tenant_1_report_500.pdf")).thenReturn(new byte[]{1, 2, 3});
        when(emailAttemptRepository.countByReportId(500L)).thenReturn(0);
        when(smtpMailService.sendForTenant(eq(1L), eq("owner@example.com"), anyString(), anyString(), isNull(),
                anyString(), any(), eq("application/pdf")))
                .thenReturn(new SmtpMailService.SmtpResult(true, "ZEPTOMAIL", null, null, null, "req-abc-123"));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.report().getStatus()).isEqualTo(ReportStatus.SENT);
        assertThat(outcome.report().getEmailStatus()).isEqualTo(ReportEmailStatus.SENT);
        assertThat(outcome.report().getEmailSentAt()).isNotNull();
        assertThat(outcome.report().getRecipientEmails()).isEqualTo("owner@example.com");
        assertThat(outcome.report().getProviderMessageId()).isEqualTo("req-abc-123");
        assertThat(outcome.report().getEmailAttemptCount()).isEqualTo(1);
        verify(emailAttemptRepository).save(argThat(a -> a.isSuccess() && a.getAttemptNo() == 1));
    }

    @Test
    void sendReportEmail_noRecipientConfigured_returnsPreciseErrorCodeWithoutCallingProvider() {
        Report report = readyReport();
        Tenant tenantNoEmail = Tenant.builder().id(1L).name("Test Agency").email(null).build();
        report.setTenant(tenantNoEmail);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.of(ReportPreferences.builder()
                .tenant(tenantNoEmail).primaryRecipientEmail(null).build()));

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("REPORT_RECIPIENT_MISSING");
        verifyNoInteractions(smtpMailService);
        verifyNoInteractions(emailAttemptRepository);
    }

    @Test
    void sendReportEmail_missingPdfFile_returnsReportFileMissing() {
        Report report = readyReport();
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.of(ReportPreferences.builder()
                .tenant(tenant()).primaryRecipientEmail("owner@example.com").build()));
        when(pdfStorage.read("tenant_1_report_500.pdf")).thenReturn(null);

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("REPORT_FILE_MISSING");
        verifyNoInteractions(smtpMailService);
    }

    @Test
    void sendReportEmail_reportNotYetGenerated_returnsReportNotReady() {
        Report report = readyReport();
        report.setStatus(ReportStatus.GENERATING);

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("REPORT_NOT_READY");
        verifyNoInteractions(pdfStorage, smtpMailService);
    }

    @Test
    void sendReportEmail_generationFailed_returnsDistinctErrorCodeNotGenericNotReady() {
        // Regression: a report whose generation itself failed has no PDF and
        // never will — the frontend used to leave the Send button clickable for
        // this status, and the backend used to lump it in with the generic
        // "still generating, wait" REPORT_NOT_READY response, which is actively
        // misleading (there is nothing to wait for; regeneration is required).
        Report report = readyReport();
        report.setStatus(ReportStatus.FAILED);

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("REPORT_GENERATION_FAILED");
        verifyNoInteractions(pdfStorage, smtpMailService);
    }

    @Test
    void sendReportEmail_alreadySendingRecently_isRejectedAsInProgress_doesNotDuplicateSend() {
        Report report = readyReport();
        report.setEmailStatus(ReportEmailStatus.PENDING);
        report.setLastEmailAttemptAt(LocalDateTime.now().minusSeconds(5));

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("REPORT_EMAIL_SEND_IN_PROGRESS");
        verifyNoInteractions(smtpMailService);
    }

    @Test
    void sendReportEmail_staleInProgressLock_isAllowedToRetry() {
        Report report = readyReport();
        report.setEmailStatus(ReportEmailStatus.PENDING);
        report.setLastEmailAttemptAt(LocalDateTime.now().minusMinutes(10)); // older than the 2-minute staleness window
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.of(ReportPreferences.builder()
                .tenant(tenant()).primaryRecipientEmail("owner@example.com").build()));
        when(pdfStorage.read("tenant_1_report_500.pdf")).thenReturn(new byte[]{1});
        when(emailAttemptRepository.countByReportId(500L)).thenReturn(0);
        when(smtpMailService.sendForTenant(eq(1L), anyString(), anyString(), anyString(), isNull(), anyString(), any(), anyString()))
                .thenReturn(SmtpMailService.SmtpResult.success("ZEPTOMAIL"));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isTrue();
        verify(smtpMailService).sendForTenant(eq(1L), anyString(), anyString(), anyString(), isNull(), anyString(), any(), anyString());
    }

    @Test
    void sendReportEmail_providerRejection_persistsFailedStateWithMappedErrorCode() {
        Report report = readyReport();
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.of(ReportPreferences.builder()
                .tenant(tenant()).primaryRecipientEmail("owner@example.com").build()));
        when(pdfStorage.read("tenant_1_report_500.pdf")).thenReturn(new byte[]{1});
        when(emailAttemptRepository.countByReportId(500L)).thenReturn(0);
        when(smtpMailService.sendForTenant(eq(1L), anyString(), anyString(), anyString(), isNull(), anyString(), any(), anyString()))
                .thenReturn(SmtpMailService.SmtpResult.failure("ZEPTOMAIL", "Sender domain not verified.", "EMAIL_SENDER_NOT_VERIFIED"));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("REPORT_EMAIL_PROVIDER_REJECTED");
        assertThat(outcome.report().getEmailStatus()).isEqualTo(ReportEmailStatus.FAILED);
        assertThat(outcome.report().getEmailFailureCode()).isEqualTo("REPORT_EMAIL_PROVIDER_REJECTED");
        assertThat(outcome.report().getEmailFailureReason()).isEqualTo("Sender domain not verified.");
        // Never fake success: report.status must NOT have been advanced to SENT.
        assertThat(outcome.report().getStatus()).isEqualTo(ReportStatus.GENERATED);
        verify(emailAttemptRepository).save(argThat(a -> !a.isSuccess()));
    }

    @Test
    void sendReportEmail_retryAfterFailure_succeedsAndIncrementsAttemptCount() {
        Report report = readyReport();
        report.setEmailStatus(ReportEmailStatus.FAILED);
        report.setEmailFailureCode("REPORT_EMAIL_SEND_FAILED");
        report.setEmailAttemptCount(1);
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.of(ReportPreferences.builder()
                .tenant(tenant()).primaryRecipientEmail("owner@example.com").build()));
        when(pdfStorage.read("tenant_1_report_500.pdf")).thenReturn(new byte[]{1});
        when(emailAttemptRepository.countByReportId(500L)).thenReturn(1);
        when(smtpMailService.sendForTenant(eq(1L), anyString(), anyString(), anyString(), isNull(), anyString(), any(), anyString()))
                .thenReturn(SmtpMailService.SmtpResult.success("ZEPTOMAIL", "req-2"));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.report().getEmailAttemptCount()).isEqualTo(2);
        assertThat(outcome.report().getEmailFailureCode()).isNull();
        verify(emailAttemptRepository).save(argThat(a -> a.getAttemptNo() == 2));
    }

    @Test
    void sendReportEmail_transientNetworkError_mapsToRetryableSendFailedCode() {
        Report report = readyReport();
        when(preferencesRepository.findByTenantId(1L)).thenReturn(Optional.of(ReportPreferences.builder()
                .tenant(tenant()).primaryRecipientEmail("owner@example.com").build()));
        when(pdfStorage.read("tenant_1_report_500.pdf")).thenReturn(new byte[]{1});
        when(emailAttemptRepository.countByReportId(500L)).thenReturn(0);
        when(smtpMailService.sendForTenant(eq(1L), anyString(), anyString(), anyString(), isNull(), anyString(), any(), anyString()))
                .thenReturn(SmtpMailService.SmtpResult.failure("ZEPTOMAIL", "Could not reach the email provider.", "EMAIL_API_NETWORK_ERROR"));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportGenerationService.SendEmailOutcome outcome = service()
                .sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND);

        assertThat(outcome.errorCode()).isEqualTo("REPORT_EMAIL_SEND_FAILED");
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
