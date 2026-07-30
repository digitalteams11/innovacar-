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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single entry point for generating (and emailing) a report — used by
 * both {@link ReportSchedulerJob} and the manual-generate REST endpoint. Owns
 * entitlement checks, idempotency, the generate+email pipeline, and failure
 * isolation (spec sections 2, 15-21).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    public static final String FEATURE_MONTHLY = "AUTOMATED_MONTHLY_REPORT";
    public static final String FEATURE_YEARLY = "AUTOMATED_YEARLY_REPORT";
    public static final String FEATURE_AI_SUMMARY = "AI_REPORT_SUMMARY";
    public static final String FEATURE_MANUAL_EXPORT = "MANUAL_REPORT_EXPORT";

    private static final List<ReportStatus> ALREADY_DONE = List.of(
            ReportStatus.GENERATED, ReportStatus.EMAIL_PENDING, ReportStatus.SENT);

    private final TenantRepository tenantRepository;
    private final ReportRepository reportRepository;
    private final ReportPreferencesRepository preferencesRepository;
    private final ReportEmailAttemptRepository emailAttemptRepository;
    private final FeatureAccessService featureAccessService;
    private final ReportPeriodResolver periodResolver;
    private final ReportCalculationService calculationService;
    private final DeterministicSummaryService deterministicSummaryService;
    private final AiReportSummaryService aiReportSummaryService;
    private final ReportPdfGenerator pdfGenerator;
    private final ReportPdfStorage pdfStorage;
    private final SmtpMailService smtpMailService;
    private final ObjectMapper objectMapper;

    public enum SkipReason { NOT_ENTITLED, ACCOUNT_BLOCKED, DISABLED_BY_PREFERENCES, NO_RECIPIENT, ALREADY_EXISTS, TENANT_NOT_FOUND }

    public record GenerationOutcome(Report report, SkipReason skipReason) {
        public static GenerationOutcome skipped(SkipReason reason) { return new GenerationOutcome(null, reason); }
        public static GenerationOutcome of(Report report) { return new GenerationOutcome(report, null); }
    }

    /** Scheduler entry point — resolves the previous closed period relative to {@code referenceDate}. */
    @Transactional
    public GenerationOutcome generateScheduled(Long tenantId, ReportType type, LocalDate referenceDate) {
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty()) return GenerationOutcome.skipped(SkipReason.TENANT_NOT_FOUND);
        Tenant tenant = tenantOpt.get();

        String requiredFeature = type == ReportType.MONTHLY ? FEATURE_MONTHLY : FEATURE_YEARLY;
        if (!featureAccessService.isEnabledForTenant(tenantId, requiredFeature)) {
            return GenerationOutcome.skipped(SkipReason.NOT_ENTITLED);
        }
        if (tenant.isAccountBlocked()) {
            return GenerationOutcome.skipped(SkipReason.ACCOUNT_BLOCKED);
        }

        ReportPreferences preferences = resolvePreferences(tenant);
        if (!preferences.isReportEnabled()
                || (type == ReportType.MONTHLY && !preferences.isMonthlyReportEnabled())
                || (type == ReportType.YEARLY && !preferences.isYearlyReportEnabled())) {
            return GenerationOutcome.skipped(SkipReason.DISABLED_BY_PREFERENCES);
        }
        String recipient = resolvePrimaryRecipient(tenant, preferences);
        if (!StringUtils.hasText(recipient)) {
            return GenerationOutcome.skipped(SkipReason.NO_RECIPIENT);
        }

        ZoneId zone = periodResolver.resolveZone(preferences.getTimezone());
        ReportPeriodResolver.Period period = type == ReportType.MONTHLY
                ? periodResolver.previousClosedMonth(zone, referenceDate)
                : periodResolver.previousClosedYear(zone, referenceDate);

        if (reportRepository.existsByTenantIdAndReportTypeAndPeriodStartAndPeriodEndAndStatusIn(
                tenantId, type, period.start(), period.end(), ALREADY_DONE)) {
            return GenerationOutcome.skipped(SkipReason.ALREADY_EXISTS);
        }

        Report report = generate(tenant, type, period, preferences, zone, ReportGeneratedBy.SCHEDULER, null, true);
        return GenerationOutcome.of(report);
    }

    /** Manual/on-demand generation — gated on MANUAL_REPORT_EXPORT, always allowed for the previous closed period unless explicit year/month given. */
    @Transactional
    public GenerationOutcome generateManual(Long tenantId, ReportType type, Integer year, Integer month, Long userId,
                                             ReportGeneratedBy generatedBy, boolean forceRegenerate) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return GenerationOutcome.skipped(SkipReason.TENANT_NOT_FOUND);

        if (generatedBy != ReportGeneratedBy.SUPER_ADMIN
                && !featureAccessService.isEnabledForTenant(tenantId, FEATURE_MANUAL_EXPORT)) {
            return GenerationOutcome.skipped(SkipReason.NOT_ENTITLED);
        }
        if (tenant.isAccountBlocked()) {
            return GenerationOutcome.skipped(SkipReason.ACCOUNT_BLOCKED);
        }

        ReportPreferences preferences = resolvePreferences(tenant);
        ZoneId zone = periodResolver.resolveZone(preferences.getTimezone());
        ReportPeriodResolver.Period period = resolveManualPeriod(type, year, month, zone);

        boolean alreadyExists = reportRepository.existsByTenantIdAndReportTypeAndPeriodStartAndPeriodEndAndStatusIn(
                tenantId, type, period.start(), period.end(), ALREADY_DONE);
        if (alreadyExists && !forceRegenerate) {
            return GenerationOutcome.skipped(SkipReason.ALREADY_EXISTS);
        }

        Report report = generate(tenant, type, period, preferences, zone, generatedBy, userId, false);
        return GenerationOutcome.of(report);
    }

    /** Re-sends the stored PDF for an already-generated report — never regenerates the PDF (spec section 21). */
    @Transactional
    public Report resend(Report report) {
        Tenant tenant = report.getTenant();
        ReportPreferences preferences = resolvePreferences(tenant);
        String recipient = resolvePrimaryRecipient(tenant, preferences);
        byte[] pdfBytes = pdfStorage.read(report.getFileStorageKey());
        if (pdfBytes == null) {
            report.setEmailStatus(ReportEmailStatus.FAILED);
            report.setFailureReason("Stored PDF file is missing; cannot resend without regenerating.");
            return reportRepository.save(report);
        }
        sendEmail(report, tenant, recipient, pdfBytes, ReportEmailAttempt.TRIGGERED_BY_MANUAL_RESEND);
        return reportRepository.save(report);
    }

    // ── Core pipeline ────────────────────────────────────────────────────────

    private Report generate(Tenant tenant, ReportType type, ReportPeriodResolver.Period period,
                             ReportPreferences preferences, ZoneId zone, ReportGeneratedBy generatedBy,
                             Long userId, boolean autoEmail) {
        Long tenantId = tenant.getId();
        Report report = reportRepository
                .findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(tenantId, type, period.start(), period.end())
                .orElseGet(() -> Report.builder()
                        .tenant(tenant).reportType(type).periodStart(period.start()).periodEnd(period.end())
                        .calculationVersion(1)
                        .build());
        // Regenerating an already-generated report bumps calculationVersion rather than silently
        // overwriting history unlabeled (spec section 20) — the DB's uk_report_period constraint
        // keeps exactly one row per period, so full multi-version history isn't kept, but every
        // regeneration is at least visibly versioned.
        if (report.getId() != null && report.getStatus() != null && ALREADY_DONE.contains(report.getStatus())) {
            report.setCalculationVersion(report.getCalculationVersion() + 1);
        }
        report.setLanguage(preferences.getReportLanguage());
        report.setGeneratedBy(generatedBy);
        report.setGeneratedByUserId(userId);
        report.setStatus(ReportStatus.GENERATING);
        report.setFailureReason(null);
        report = reportRepository.save(report);

        try {
            ReportDataset dataset = calculationService.calculate(tenant, type, period, zone);

            boolean wantsAi = preferences.isIncludeAiSummary()
                    && featureAccessService.isEnabledForTenant(tenantId, FEATURE_AI_SUMMARY);
            List<String> summaryLines;
            boolean aiUsed;
            if (wantsAi) {
                AiReportSummaryService.SummaryResult result = aiReportSummaryService.buildSummary(dataset, report.getLanguage());
                summaryLines = result.lines();
                aiUsed = result.aiUsed();
            } else {
                summaryLines = deterministicSummaryService.buildSummary(dataset);
                aiUsed = false;
            }

            byte[] pdfBytes = pdfGenerator.generate(report, tenant, dataset, summaryLines, aiUsed);
            report.setGeneratedAt(LocalDateTime.now());
            ReportPdfStorage.StoredFile stored = pdfStorage.save(tenantId, report.getId(), pdfBytes);
            report.setFileStorageKey(stored.fileStorageKey());
            report.setFileSizeBytes(stored.fileSizeBytes());
            report.setChecksum(stored.checksum());
            report.setAiSummaryUsed(aiUsed);
            report.setCalculationSnapshot(objectMapper.writeValueAsString(dataset));
            report.setStatus(ReportStatus.GENERATED);
            report = reportRepository.save(report);

            if (autoEmail) {
                String recipient = resolvePrimaryRecipient(tenant, preferences);
                if (StringUtils.hasText(recipient)) {
                    sendEmail(report, tenant, recipient, pdfBytes, ReportEmailAttempt.TRIGGERED_BY_SYSTEM);
                    report = reportRepository.save(report);
                }
            }
            return report;
        } catch (Exception e) {
            log.error("[REPORT_GENERATION] Failed for tenantId={} type={} period={}..{}: {}",
                    tenantId, type, period.start(), period.end(), e.getMessage(), e);
            report.setStatus(ReportStatus.FAILED);
            report.setFailureReason(safeMessage(e));
            return reportRepository.save(report);
        }
    }

    private void sendEmail(Report report, Tenant tenant, String recipient, byte[] pdfBytes, String triggeredBy) {
        String subject = emailSubject(tenant, report);
        String html = emailBody(tenant, report);
        int attemptNo = emailAttemptRepository.countByReportId(report.getId()) + 1;
        String attachmentName = "report_" + report.getReportType().name().toLowerCase()
                + "_" + report.getPeriodStart().toLocalDate() + ".pdf";
        try {
            SmtpMailService.SmtpResult result = smtpMailService.sendForTenant(
                    tenant.getId(), recipient, subject, html, null, attachmentName, pdfBytes, "application/pdf");
            emailAttemptRepository.save(ReportEmailAttempt.builder()
                    .report(report).attemptNo(attemptNo).triggeredBy(triggeredBy)
                    .recipientEmails(recipient).success(result.sent())
                    .errorMessage(result.sent() ? null : truncate(result.errorMessage(), 900))
                    .build());
            report.setRecipientEmails(recipient);
            report.setEmailSentAt(result.sent() ? LocalDateTime.now() : report.getEmailSentAt());
            report.setEmailStatus(result.sent() ? ReportEmailStatus.SENT : ReportEmailStatus.FAILED);
            report.setStatus(result.sent() ? ReportStatus.SENT : report.getStatus());
        } catch (Exception e) {
            log.error("[REPORT_EMAIL] Failed to send reportId={} to={}: {}", report.getId(), recipient, e.getMessage(), e);
            emailAttemptRepository.save(ReportEmailAttempt.builder()
                    .report(report).attemptNo(attemptNo).triggeredBy(triggeredBy)
                    .recipientEmails(recipient).success(false).errorMessage(truncate(e.getMessage(), 900))
                    .build());
            report.setEmailStatus(ReportEmailStatus.FAILED);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ReportPeriodResolver.Period resolveManualPeriod(ReportType type, Integer year, Integer month, ZoneId zone) {
        LocalDate now = LocalDate.now(zone);
        if (type == ReportType.YEARLY) {
            int y = year != null ? year : now.getYear() - 1;
            return periodResolver.previousClosedYear(zone, LocalDate.of(y + 1, 1, 1));
        }
        if (year != null && month != null) {
            LocalDate firstOfRequestedMonth = LocalDate.of(year, month, 1);
            return periodResolver.previousClosedMonth(zone, firstOfRequestedMonth.plusMonths(1));
        }
        return periodResolver.previousClosedMonth(zone, now);
    }

    private ReportPreferences resolvePreferences(Tenant tenant) {
        return preferencesRepository.findByTenantId(tenant.getId()).orElseGet(() -> ReportPreferences.builder()
                .tenant(tenant)
                .reportEnabled(true).monthlyReportEnabled(true).yearlyReportEnabled(true)
                .reportLanguage("fr")
                .includeAiSummary(true).includeClientDebtDetail(true)
                .primaryRecipientEmail(tenant.getEmail())
                .build());
    }

    private String resolvePrimaryRecipient(Tenant tenant, ReportPreferences preferences) {
        if (StringUtils.hasText(preferences.getPrimaryRecipientEmail())) return preferences.getPrimaryRecipientEmail();
        return tenant.getEmail();
    }

    private String emailSubject(Tenant tenant, Report report) {
        String period = report.getReportType() == ReportType.MONTHLY
                ? report.getPeriodStart().getMonth() + " " + report.getPeriodStart().getYear()
                : String.valueOf(report.getPeriodStart().getYear());
        String title = ReportLabels.get(report.getReportType() == ReportType.MONTHLY ? "title.monthly" : "title.yearly",
                report.getLanguage());
        return "Innovacar - " + title + " - " + period;
    }

    private String emailBody(Tenant tenant, Report report) {
        return "<p>" + (tenant != null ? tenant.getName() : "") + "</p>"
                + "<p>" + ReportLabels.get("cover.period", report.getLanguage()) + ": "
                + report.getPeriodStart().toLocalDate() + " - " + report.getPeriodEnd().toLocalDate().minusDays(1) + "</p>"
                + "<p>Please find your report attached as a secure PDF.</p>";
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return truncate(message != null ? message : e.getClass().getSimpleName(), 900);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
