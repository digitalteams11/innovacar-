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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /** A report is eligible to be emailed once it has a real PDF on disk — GENERATED (first send) or SENT (resend). */
    private static final Set<ReportStatus> EMAIL_ELIGIBLE_STATUSES = Set.of(ReportStatus.GENERATED, ReportStatus.SENT);

    /**
     * How long an in-flight PENDING send is trusted before being treated as stale
     * (e.g. the app instance crashed mid-send) and allowed to be retried — a
     * best-effort duplicate-send guard, not a strict distributed lock.
     */
    private static final Duration PENDING_SEND_STALE_AFTER = Duration.ofMinutes(2);

    /** Provider-level error codes (see {@link com.carrental.service.HttpEmailProvider}) that mean the provider actively rejected the request rather than a transient/network failure. */
    private static final Set<String> PROVIDER_REJECTION_CODES = Set.of(
            "EMAIL_SENDER_NOT_VERIFIED", "EMAIL_API_UNAUTHORIZED", "EMAIL_API_REQUEST_REJECTED",
            "EMAIL_API_INVALID_PAYLOAD", "EMAIL_CONFIGURATION_MISSING", "EMAIL_API_ENDPOINT_INVALID");

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
    private final com.carrental.service.EmailTemplateRenderer emailTemplateRenderer;
    private final com.carrental.service.EmailActionUrlBuilder emailActionUrlBuilder;
    private final ObjectMapper objectMapper;

    public enum SkipReason {
        NOT_ENTITLED, ACCOUNT_BLOCKED, DISABLED_BY_PREFERENCES, NO_RECIPIENT, ALREADY_EXISTS,
        TENANT_NOT_FOUND, PERIOD_NOT_CLOSED
    }

    public record GenerationOutcome(Report report, SkipReason skipReason) {
        public static GenerationOutcome skipped(SkipReason reason) { return new GenerationOutcome(null, reason); }
        /** Carries the conflicting/existing report along with the skip reason — used for ALREADY_EXISTS so the caller can link to it (spec section 5/9). */
        public static GenerationOutcome skippedWithReport(SkipReason reason, Report report) { return new GenerationOutcome(report, reason); }
        public static GenerationOutcome of(Report report) { return new GenerationOutcome(report, null); }
    }

    /** Structured result of an email send/resend attempt — never a bare boolean, per spec section 8. */
    public record SendEmailOutcome(boolean success, String errorCode, String message, Report report) {
        public static SendEmailOutcome ok(Report report, String message) {
            return new SendEmailOutcome(true, null, message, report);
        }
        public static SendEmailOutcome fail(String errorCode, String message, Report report) {
            return new SendEmailOutcome(false, errorCode, message, report);
        }
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

        // The period must be fully closed — never generate for the current
        // incomplete month/year or a future one, even on an explicit request
        // (spec sections 3/4/12).
        if (period.end().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
            return GenerationOutcome.skipped(SkipReason.PERIOD_NOT_CLOSED);
        }

        if (!forceRegenerate) {
            Optional<Report> existing = reportRepository
                    .findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(tenantId, type, period.start(), period.end())
                    .filter(r -> ALREADY_DONE.contains(r.getStatus()));
            if (existing.isPresent()) {
                return GenerationOutcome.skippedWithReport(SkipReason.ALREADY_EXISTS, existing.get());
            }
        }

        Report report = generate(tenant, type, period, preferences, zone, generatedBy, userId, false);
        return GenerationOutcome.of(report);
    }

    /**
     * The single entry point for sending or re-sending a report's email — used
     * by the manual "Send"/"Resend" endpoint and by the scheduler's auto-email
     * step. Never regenerates the PDF (spec section 21): only ever reads the
     * already-stored file. Returns a structured outcome instead of a bare
     * report/boolean so the caller can surface a precise reason on failure
     * (spec section 8) — the frontend must never collapse every failure into
     * one generic "Failed to send email" message again.
     */
    @Transactional
    public SendEmailOutcome sendReportEmail(Report report, String triggeredBy) {
        if (report.getStatus() == ReportStatus.FAILED) {
            // Distinct from "still generating" — generation already failed, there
            // is no PDF and never will be one for this row. Telling the user to
            // "wait for generation to finish" here would be actively misleading;
            // the only path forward is regenerating the report.
            return SendEmailOutcome.fail("REPORT_GENERATION_FAILED",
                    "Report generation failed, so there is no file to send. Regenerate the report first.", report);
        }
        if (report.getStatus() == null || !EMAIL_ELIGIBLE_STATUSES.contains(report.getStatus())) {
            return SendEmailOutcome.fail("REPORT_NOT_READY",
                    "The report is not ready to be emailed yet. Wait for generation to finish.", report);
        }
        if (report.getEmailStatus() == ReportEmailStatus.PENDING && report.getLastEmailAttemptAt() != null
                && report.getLastEmailAttemptAt().isAfter(LocalDateTime.now().minus(PENDING_SEND_STALE_AFTER))) {
            // Backend-side duplicate-send guard (spec section 7) — a second click/request
            // arriving while a send is already in flight for this exact report is rejected
            // rather than firing a second email. Not a strict distributed lock (a crashed
            // request would leave PENDING behind), which is why it self-expires after
            // PENDING_SEND_STALE_AFTER instead of locking the report forever.
            return SendEmailOutcome.fail("REPORT_EMAIL_SEND_IN_PROGRESS",
                    "An email send for this report is already in progress.", report);
        }
        if (!StringUtils.hasText(report.getFileStorageKey())) {
            return SendEmailOutcome.fail("REPORT_FILE_MISSING", "The report PDF file is missing.", report);
        }

        Tenant tenant = report.getTenant();
        ReportPreferences preferences = resolvePreferences(tenant);
        String recipient = resolvePrimaryRecipient(tenant, preferences);
        if (!StringUtils.hasText(recipient)) {
            return SendEmailOutcome.fail("REPORT_RECIPIENT_MISSING", "No report recipient email is configured.", report);
        }

        byte[] pdfBytes;
        try {
            pdfBytes = pdfStorage.read(report.getFileStorageKey());
        } catch (Exception e) {
            log.error("[REPORT_EMAIL] Failed to read stored PDF for reportId={}: {}", report.getId(), e.getMessage(), e);
            pdfBytes = null;
        }
        if (pdfBytes == null) {
            return SendEmailOutcome.fail("REPORT_FILE_MISSING", "The report PDF file could not be read.", report);
        }

        // Mark in-flight and persist immediately, before the (potentially slow) provider
        // call, so a concurrent duplicate request sees PENDING rather than the pre-send state.
        report.setEmailStatus(ReportEmailStatus.PENDING);
        report.setLastEmailAttemptAt(LocalDateTime.now());
        report = reportRepository.save(report);

        return performSend(report, tenant, recipient, pdfBytes, triggeredBy);
    }

    private SendEmailOutcome performSend(Report report, Tenant tenant, String recipient, byte[] pdfBytes, String triggeredBy) {
        String subject = emailSubject(tenant, report);
        String html = emailBody(tenant, report);
        int attemptNo = emailAttemptRepository.countByReportId(report.getId()) + 1;
        String attachmentName = "report_" + report.getReportType().name().toLowerCase()
                + "_" + report.getPeriodStart().toLocalDate() + ".pdf";

        SmtpMailService.SmtpResult result;
        try {
            result = smtpMailService.sendForTenant(
                    tenant.getId(), recipient, subject, html, null, attachmentName, pdfBytes, "application/pdf");
        } catch (Exception e) {
            log.error("[REPORT_EMAIL] Unexpected error sending reportId={} to={}: {}", report.getId(), recipient, e.getMessage(), e);
            result = SmtpMailService.SmtpResult.failure("ZEPTOMAIL", e.getMessage(), "REPORT_ATTACHMENT_FAILED");
        }

        report.setEmailAttemptCount(report.getEmailAttemptCount() + 1);
        emailAttemptRepository.save(ReportEmailAttempt.builder()
                .report(report).attemptNo(attemptNo).triggeredBy(triggeredBy)
                .recipientEmails(recipient).success(result.sent())
                .errorMessage(result.sent() ? null : truncate(result.errorMessage(), 900))
                .build());

        if (result.sent()) {
            report.setRecipientEmails(recipient);
            report.setEmailSentAt(LocalDateTime.now());
            report.setEmailStatus(ReportEmailStatus.SENT);
            report.setProviderMessageId(result.messageId());
            report.setEmailFailureCode(null);
            report.setEmailFailureReason(null);
            if (report.getStatus() == ReportStatus.GENERATED) report.setStatus(ReportStatus.SENT);
            report = reportRepository.save(report);
            log.info("[REPORT_EMAIL] Sent reportId={} tenantId={} recipientDomain={} providerMessageId={}",
                    report.getId(), tenant.getId(), domainOf(recipient), result.messageId());
            return SendEmailOutcome.ok(report, "Report email sent successfully.");
        }

        String errorCode = "REPORT_ATTACHMENT_FAILED".equals(result.errorCode())
                ? "REPORT_ATTACHMENT_FAILED"
                : (PROVIDER_REJECTION_CODES.contains(result.errorCode()) ? "REPORT_EMAIL_PROVIDER_REJECTED" : "REPORT_EMAIL_SEND_FAILED");
        String message = StringUtils.hasText(result.errorMessage())
                ? result.errorMessage() : "The report could not be sent. Please try again.";
        report.setEmailStatus(ReportEmailStatus.FAILED);
        report.setEmailFailureCode(errorCode);
        report.setEmailFailureReason(truncate(message, 900));
        report = reportRepository.save(report);
        log.warn("[REPORT_EMAIL] Failed reportId={} tenantId={} errorCode={} providerErrorCode={}",
                report.getId(), tenant.getId(), errorCode, result.errorCode());
        return SendEmailOutcome.fail(errorCode, message, report);
    }

    private String domainOf(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at >= 0 && at < email.length() - 1 ? email.substring(at + 1) : null;
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
                SendEmailOutcome emailOutcome = sendReportEmail(report, ReportEmailAttempt.TRIGGERED_BY_SYSTEM);
                report = emailOutcome.report();
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
        String periodLabel = report.getPeriodStart().toLocalDate() + " - " + report.getPeriodEnd().toLocalDate().minusDays(1);
        String templateName = report.getReportType() == ReportType.MONTHLY ? "monthly-report" : "yearly-report";
        // The CTA must open this exact report in the report archive, not the generic
        // dashboard — a mislabeled "Open dashboard" button that actually opened an
        // unrelated page (or, before this fix, silently never rendered at all because
        // th:if="${dashboardUrl}" saw an always-null variable) was the reported bug.
        return emailTemplateRenderer.render(templateName, java.util.Map.of(
                "tenantName", tenant != null && tenant.getName() != null ? tenant.getName() : "there",
                "periodLabel", periodLabel,
                "reportUrl", emailActionUrlBuilder.reportArchiveUrl(report.getId()),
                "buttonLabel", ReportLabels.get("button.viewReport", report.getLanguage())));
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
