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

    @PostMapping("/reports/generate")
    public ResponseEntity<Map<String, Object>> generateReport(@RequestBody Map<String, Object> body) {
        Long tenantId = requireTenantId();
        ReportType type = ReportType.valueOf(String.valueOf(body.getOrDefault("reportType", "MONTHLY")).toUpperCase());
        Integer year = body.get("year") != null ? Integer.valueOf(String.valueOf(body.get("year"))) : null;
        Integer month = body.get("month") != null ? Integer.valueOf(String.valueOf(body.get("month"))) : null;
        boolean force = Boolean.TRUE.equals(body.get("forceRegenerate"));

        ReportGenerationService.GenerationOutcome outcome = reportGenerationService
                .generateManual(tenantId, type, year, month, null, ReportGeneratedBy.MANUAL, force);

        Map<String, Object> response = new LinkedHashMap<>();
        if (outcome.skipReason() != null) {
            response.put("success", false);
            response.put("reason", outcome.skipReason().name());
            return ResponseEntity.status(outcome.skipReason() == ReportGenerationService.SkipReason.NOT_ENTITLED ? 403 : 400)
                    .body(response);
        }
        response.put("success", true);
        response.put("report", toDetail(outcome.report()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reports/{id}/resend")
    public ResponseEntity<Map<String, Object>> resendReport(@PathVariable Long id) {
        requireFeature(FEATURE_ARCHIVE);
        Report report = requireOwnedReport(id);
        Report resent = reportGenerationService.resend(report);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", resent.getEmailStatus().name().equals("SENT"));
        response.put("report", toDetail(resent));
        return ResponseEntity.ok(response);
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
        return row;
    }

    private Map<String, Object> toDetail(Report r) {
        Map<String, Object> row = toSummary(r);
        row.put("generatedBy", r.getGeneratedBy());
        row.put("failureReason", r.getFailureReason());
        row.put("aiSummaryUsed", r.isAiSummaryUsed());
        row.put("calculationVersion", r.getCalculationVersion());
        row.put("recipientEmails", r.getRecipientEmails());
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
