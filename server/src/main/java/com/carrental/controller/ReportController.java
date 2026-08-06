package com.carrental.controller;

import com.carrental.entity.Report;
import com.carrental.entity.ReportPreferences;
import com.carrental.entity.ReportGeneratedBy;
import com.carrental.entity.ReportType;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ReportPreferencesRepository;
import com.carrental.repository.ReportRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.FeatureAccessService;
import com.carrental.service.reporting.ReportGenerationService;
import com.carrental.service.reporting.ReportPdfStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report archive + manual generation API — spec section 24. Every endpoint is
 * tenant-scoped via {@link TenantContext} (never trusts a client-supplied
 * tenant id) and feature-gated via {@link FeatureAccessService}, matching
 * this codebase's existing entitlement conventions.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private static final String FEATURE_ARCHIVE = "REPORT_ARCHIVE";

    private final ReportRepository reportRepository;
    private final ReportPreferencesRepository preferencesRepository;
    private final TenantRepository tenantRepository;
    private final FeatureAccessService featureAccessService;
    private final ReportGenerationService reportGenerationService;
    private final ReportPdfStorage pdfStorage;

    @GetMapping("/reports")
    public ResponseEntity<List<Map<String, Object>>> listReports(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        requireFeature(FEATURE_ARCHIVE);
        Long tenantId = requireTenantId();
        List<Report> reports = reportRepository.findAllByTenantIdOrderByPeriodStartDesc(tenantId);
        List<Map<String, Object>> rows = reports.stream()
                .filter(r -> type == null || r.getReportType().name().equalsIgnoreCase(type))
                .filter(r -> status == null || r.getStatus().name().equalsIgnoreCase(status))
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<Map<String, Object>> getReport(@PathVariable Long id) {
        requireFeature(FEATURE_ARCHIVE);
        Report report = requireOwnedReport(id);
        return ResponseEntity.ok(toDetail(report));
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        requireFeature(FEATURE_ARCHIVE);
        Report report = requireOwnedReport(id);
        byte[] pdfBytes = report.getFileStorageKey() != null ? pdfStorage.read(report.getFileStorageKey()) : null;
        if (pdfBytes == null) {
            throw new ResourceNotFoundException("Report PDF file not found");
        }
        String fileName = "report_" + report.getReportType().name().toLowerCase()
                + "_" + report.getPeriodStart().toLocalDate() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(pdfBytes);
    }

    /**
     * Validates the request contract strictly and returns structured JSON on
     * every failure path — never an empty 400 (spec sections 1, 2, 9). The
     * only two ways to reach a non-2xx response are (a) a malformed request
     * (bad type/year/month — 400 with fieldErrors) or (b) a business-rule
     * skip reason mapped to its own status/errorCode below; nothing falls
     * through to a generic exception-mapped 400/500.
     */
    @PostMapping("/reports/generate")
    public ResponseEntity<Map<String, Object>> generateReport(@RequestBody(required = false) Map<String, Object> rawBody) {
        Long tenantId = requireTenantId();
        Map<String, Object> body = rawBody != null ? rawBody : Map.of();

        ReportType type;
        String typeRaw = String.valueOf(body.getOrDefault("reportType", "MONTHLY")).toUpperCase();
        try {
            type = ReportType.valueOf(typeRaw);
        } catch (IllegalArgumentException e) {
            return validationError("REPORT_TYPE_INVALID", "reportType must be MONTHLY or YEARLY.",
                    Map.of("reportType", "Must be MONTHLY or YEARLY."));
        }

        Integer year;
        try {
            year = parseIntOrNull(body.get("year"));
        } catch (NumberFormatException e) {
            return validationError("REPORT_PERIOD_INVALID", "year must be a whole number.", Map.of("year", "Must be a whole number."));
        }
        Integer month;
        try {
            month = parseIntOrNull(body.get("month"));
        } catch (NumberFormatException e) {
            return validationError("REPORT_PERIOD_INVALID", "month must be a whole number.", Map.of("month", "Must be a whole number."));
        }

        if (month != null && (month < 1 || month > 12)) {
            return validationError("REPORT_PERIOD_INVALID", "Month must be between 1 and 12.",
                    Map.of("month", "Must be between 1 and 12."));
        }
        if (type == ReportType.MONTHLY && (year == null) != (month == null)) {
            // A closed month is identified by year+month together — accepting only one would
            // silently resolve against the wrong year (see resolveManualPeriod), so require both
            // or neither rather than guessing.
            return validationError("REPORT_PERIOD_INVALID", "Provide both year and month, or neither to use the previous closed month.",
                    Map.of(year == null ? "year" : "month", "Required when the other period field is provided."));
        }
        if (type == ReportType.YEARLY) {
            month = null; // spec section 4 — a yearly request never carries a month
        }

        boolean force = Boolean.TRUE.equals(body.get("forceRegenerate"));

        ReportGenerationService.GenerationOutcome outcome = reportGenerationService
                .generateManual(tenantId, type, year, month, null, ReportGeneratedBy.MANUAL, force);

        if (outcome.skipReason() != null) {
            return buildSkipResponse(outcome.skipReason(), outcome.report());
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("report", toDetail(outcome.report()));
        return ResponseEntity.ok(response);
    }

    private record SkipSpec(int status, String errorCode, String message) {}

    private ResponseEntity<Map<String, Object>> buildSkipResponse(ReportGenerationService.SkipReason reason, Report existingReport) {
        SkipSpec spec = switch (reason) {
            case NOT_ENTITLED -> new SkipSpec(402, "REPORTING_FEATURE_NOT_INCLUDED",
                    "Your current plan does not include automated report generation.");
            case ACCOUNT_BLOCKED -> new SkipSpec(403, "ACCOUNT_BLOCKED", "This account is currently blocked.");
            case TENANT_NOT_FOUND -> new SkipSpec(403, "TENANT_NOT_FOUND", "Tenant could not be resolved.");
            case DISABLED_BY_PREFERENCES -> new SkipSpec(400, "REPORT_DISABLED_BY_PREFERENCES",
                    "Automated reports are disabled in your report settings.");
            case NO_RECIPIENT -> new SkipSpec(400, "REPORT_RECIPIENT_MISSING", "No report recipient email is configured.");
            case PERIOD_NOT_CLOSED -> new SkipSpec(400, "REPORT_PERIOD_INVALID",
                    "Reports can only be generated for a fully completed period, not the current or a future one.");
            case ALREADY_EXISTS -> new SkipSpec(409, "REPORT_ALREADY_EXISTS",
                    "A report already exists for " + periodLabel(existingReport) + ".");
        };

        Map<String, Object> data = new LinkedHashMap<>();
        if (existingReport != null) data.put("reportId", existingReport.getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("errorCode", spec.errorCode());
        response.put("message", spec.message());
        response.put("data", data);
        return ResponseEntity.status(spec.status()).body(response);
    }

    private ResponseEntity<Map<String, Object>> validationError(String errorCode, String message, Map<String, String> fieldErrors) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("errorCode", errorCode);
        response.put("message", message);
        response.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }

    private String periodLabel(Report report) {
        if (report == null || report.getPeriodStart() == null) return "this period";
        java.time.LocalDate start = report.getPeriodStart().toLocalDate();
        return report.getReportType() == ReportType.YEARLY
                ? String.valueOf(start.getYear())
                : start.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + start.getYear();
    }

    private Integer parseIntOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        return Integer.valueOf(text);
    }

    /**
     * Sends (first send) or re-sends the already-generated report PDF by email.
     * One endpoint covers both — the only difference is the {@code triggeredBy}
     * tag recorded on the {@code ReportEmailAttempt} row, which the frontend
     * controls implicitly via which action label it showed (Send vs Resend).
     * Never regenerates the PDF (spec section 21) and never reports SENT
     * without a real provider-confirmed send (spec section 6).
     */
    @PostMapping("/reports/{id}/send-email")
    public ResponseEntity<Map<String, Object>> sendReportEmail(@PathVariable Long id) {
        requireFeature(FEATURE_ARCHIVE);
        Report report = requireOwnedReport(id);
        String triggeredBy = report.getStatus() == com.carrental.entity.ReportStatus.SENT
                ? com.carrental.entity.ReportEmailAttempt.TRIGGERED_BY_MANUAL_RESEND
                : com.carrental.entity.ReportEmailAttempt.TRIGGERED_BY_MANUAL_SEND;
        ReportGenerationService.SendEmailOutcome outcome = reportGenerationService.sendReportEmail(report, triggeredBy);
        return buildSendEmailResponse(outcome);
    }

    /** Deprecated alias kept for any existing callers — identical behavior to {@code /send-email}. */
    @PostMapping("/reports/{id}/resend")
    public ResponseEntity<Map<String, Object>> resendReport(@PathVariable Long id) {
        return sendReportEmail(id);
    }

    private ResponseEntity<Map<String, Object>> buildSendEmailResponse(ReportGenerationService.SendEmailOutcome outcome) {
        Report report = outcome.report();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportId", report.getId());
        data.put("emailStatus", report.getEmailStatus());
        data.put("emailSentAt", report.getEmailSentAt());
        data.put("recipient", report.getRecipientEmails());

        Map<String, Object> response = new LinkedHashMap<>();
        if (outcome.success()) {
            response.put("success", true);
            response.put("message", outcome.message());
            response.put("data", data);
            return ResponseEntity.ok(response);
        }

        response.put("success", false);
        response.put("errorCode", outcome.errorCode());
        response.put("message", outcome.message());
        response.put("data", data);
        int status = switch (outcome.errorCode()) {
            case "REPORT_RECIPIENT_MISSING", "REPORT_NOT_READY", "REPORT_GENERATION_FAILED" -> 400;
            default -> 200; // provider/attachment/transient failures are a legitimate business outcome, not a bad request
        };
        return ResponseEntity.status(status).body(response);
    }

    // ── Preferences ──────────────────────────────────────────────────────────

    @GetMapping("/report-preferences")
    public ResponseEntity<Map<String, Object>> getPreferences() {
        Long tenantId = requireTenantId();
        ReportPreferences preferences = preferencesRepository.findByTenantId(tenantId).orElse(null);
        return ResponseEntity.ok(toPreferencesMap(preferences, tenantId));
    }

    @PutMapping("/report-preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(@RequestBody Map<String, Object> body) {
        Long tenantId = requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        ReportPreferences preferences = preferencesRepository.findByTenantId(tenantId)
                .orElseGet(() -> ReportPreferences.builder().tenant(tenant).build());

        preferences.setReportEnabled(boolValue(body, "reportEnabled", preferences.isReportEnabled()));
        preferences.setMonthlyReportEnabled(boolValue(body, "monthlyReportEnabled", preferences.isMonthlyReportEnabled()));
        preferences.setYearlyReportEnabled(boolValue(body, "yearlyReportEnabled", preferences.isYearlyReportEnabled()));
        preferences.setReportLanguage(stringValue(body, "reportLanguage", preferences.getReportLanguage() != null ? preferences.getReportLanguage() : "fr"));
        preferences.setTimezone(stringValue(body, "timezone", preferences.getTimezone()));
        preferences.setPrimaryRecipientEmail(stringValue(body, "primaryRecipientEmail", preferences.getPrimaryRecipientEmail()));
        preferences.setAdditionalRecipientEmails(stringValue(body, "additionalRecipientEmails", preferences.getAdditionalRecipientEmails()));
        preferences.setIncludeAiSummary(boolValue(body, "includeAiSummary", preferences.isIncludeAiSummary()));
        preferences.setIncludeClientDebtDetail(boolValue(body, "includeClientDebtDetail", preferences.isIncludeClientDebtDetail()));

        preferences = preferencesRepository.save(preferences);
        return ResponseEntity.ok(toPreferencesMap(preferences, tenantId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireFeature(String featureCode) {
        if (!featureAccessService.isEnabledForCurrentTenant(featureCode)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Feature not available on the current plan: " + featureCode);
        }
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) throw new ResourceNotFoundException("Tenant not resolved");
        return tenantId;
    }

    private Report requireOwnedReport(Long id) {
        Long tenantId = requireTenantId();
        return reportRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    private Map<String, Object> toSummary(Report r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("reportType", r.getReportType());
        row.put("periodStart", r.getPeriodStart());
        row.put("periodEnd", r.getPeriodEnd());
        row.put("status", r.getStatus());
        row.put("emailStatus", r.getEmailStatus());
        row.put("generatedAt", r.getGeneratedAt());
        row.put("emailSentAt", r.getEmailSentAt());
        row.put("language", r.getLanguage());
        row.put("emailFailureCode", r.getEmailFailureCode());
        row.put("emailFailureReason", r.getEmailFailureReason());
        row.put("recipientEmails", r.getRecipientEmails());
        return row;
    }

    private Map<String, Object> toDetail(Report r) {
        Map<String, Object> row = toSummary(r);
        row.put("generatedBy", r.getGeneratedBy());
        row.put("failureReason", r.getFailureReason());
        row.put("aiSummaryUsed", r.isAiSummaryUsed());
        row.put("calculationVersion", r.getCalculationVersion());
        row.put("emailAttemptCount", r.getEmailAttemptCount());
        row.put("lastEmailAttemptAt", r.getLastEmailAttemptAt());
        return row;
    }

    private Map<String, Object> toPreferencesMap(ReportPreferences p, Long tenantId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantId", tenantId);
        row.put("reportEnabled", p == null || p.isReportEnabled());
        row.put("monthlyReportEnabled", p == null || p.isMonthlyReportEnabled());
        row.put("yearlyReportEnabled", p == null || p.isYearlyReportEnabled());
        row.put("reportLanguage", p != null ? p.getReportLanguage() : "fr");
        row.put("timezone", p != null ? p.getTimezone() : null);
        row.put("primaryRecipientEmail", p != null ? p.getPrimaryRecipientEmail() : null);
        row.put("additionalRecipientEmails", p != null ? p.getAdditionalRecipientEmails() : null);
        row.put("includeAiSummary", p == null || p.isIncludeAiSummary());
        row.put("includeClientDebtDetail", p == null || p.isIncludeClientDebtDetail());
        return row;
    }

    private boolean boolValue(Map<String, Object> body, String key, boolean fallback) {
        Object v = body.get(key);
        return v != null ? Boolean.parseBoolean(String.valueOf(v)) : fallback;
    }

    private String stringValue(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        return v != null ? String.valueOf(v) : fallback;
    }
}
