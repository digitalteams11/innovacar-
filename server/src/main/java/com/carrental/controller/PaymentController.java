package com.carrental.controller;

import com.carrental.dto.payment.PaymentResponse;
import com.carrental.dto.payment.PaymentStatsResponse;
import com.carrental.dto.payment.RecordPaymentRequest;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.PaymentService;
import com.carrental.service.RolePermissionService;
import com.carrental.service.export.ExportHttpUtil;
import com.carrental.service.export.GenericPdfTableExporter;
import com.carrental.service.export.ReportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment REST controller.
 * Core financial transaction API for the platform.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RolePermissionService rolePermissionService;
    private final ReportExportService reportExportService;
    private final GenericPdfTableExporter pdfTableExporter;
    private final TenantRepository tenantRepository;

    // ── GET /api/payments ────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<List<PaymentResponse>> getAllPayments(Authentication authentication) {
        debugLog(authentication, "PAYMENT_VIEW", "PAYMENTS", "GET /api/payments");
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    // ── GET /api/payments/{id} ───────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    // ── POST /api/payments ───────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('RECORD_PAYMENT')")
    public ResponseEntity<PaymentResponse> recordPayment(@RequestBody RecordPaymentRequest request) {
        return ResponseEntity.ok(paymentService.recordPayment(request));
    }

    // ── POST /api/payments/{id}/refund ───────────────────────────────────────

    @PostMapping("/{id}/refund")
    @PreAuthorize("@rolePermissionService.has('RECORD_PAYMENT')")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = request.get("amount") != null
                ? new BigDecimal(request.get("amount").toString())
                : BigDecimal.ZERO;
        String reason = request.get("reason") != null ? request.get("reason").toString() : "";
        return ResponseEntity.ok(paymentService.refundPayment(id, amount, reason));
    }

    // ── GET /api/payments/stats ──────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_STATS_VIEW')")
    public ResponseEntity<PaymentStatsResponse> getPaymentStats(Authentication authentication) {
        debugLog(authentication, "PAYMENT_STATS_VIEW", "PAYMENTS", "GET /api/payments/stats");
        return ResponseEntity.ok(paymentService.getPaymentStats());
    }

    // ── GET /api/payments/monthly-revenue ────────────────────────────────────

    @GetMapping("/monthly-revenue")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_STATS_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyRevenue(Authentication authentication) {
        debugLog(authentication, "PAYMENT_STATS_VIEW", "PAYMENTS", "GET /api/payments/monthly-revenue");
        return ResponseEntity.ok(paymentService.getMonthlyRevenue());
    }

    // ── GET /api/payments/client/{clientId} ──────────────────────────────────

    @GetMapping("/client/{clientId}")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<List<PaymentResponse>> getClientPaymentHistory(@PathVariable Long clientId) {
        return ResponseEntity.ok(paymentService.getClientPaymentHistory(clientId));
    }

    // ── GET /api/payments/contract/{contractId} ──────────────────────────────

    @GetMapping("/contract/{contractId}")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<List<PaymentResponse>> getContractPayments(@PathVariable Long contractId) {
        return ResponseEntity.ok(paymentService.getContractPayments(contractId));
    }

    // ── GET /api/payments/reservation/{reservationId} ────────────────────────

    @GetMapping("/reservation/{reservationId}")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.getPaymentByReservation(reservationId));
    }

    // ── PATCH /api/payments/reservation/{reservationId}/pay ──────────────────

    @PatchMapping("/reservation/{reservationId}/pay")
    @PreAuthorize("@rolePermissionService.has('RECORD_PAYMENT')")
    public ResponseEntity<PaymentResponse> markReservationPaymentAsPaid(@PathVariable Long reservationId) {
        return ResponseEntity.ok(paymentService.markAsPaid(reservationId));
    }

    // ── Debug helper ──────────────────────────────────────────────────────────

    private void debugLog(Authentication authentication, String requiredPermission, String requiredFeature, String endpoint) {
        if (!log.isInfoEnabled()) return;
        try {
            User user = authentication != null && authentication.getPrincipal() instanceof User u ? u : null;
            boolean hasPermission = rolePermissionService.has(requiredPermission);
            log.debug("[PAYMENTS_ACCESS_DEBUG] endpoint={} userId={} email={} role={} agencyId={} requiredPermission={} hasPermission={} requiredFeature={} result={}",
                    endpoint,
                    user != null ? user.getId() : "unknown",
                    user != null ? user.getEmail() : "unknown",
                    user != null && user.getRole() != null ? user.getRole().name() : "unknown",
                    user != null && user.getTenant() != null ? user.getTenant().getId() : "null",
                    requiredPermission,
                    hasPermission,
                    requiredFeature,
                    hasPermission ? "ALLOW" : "DENY");
        } catch (Exception ex) {
            log.debug("[PAYMENTS_ACCESS_DEBUG] failed to log access details: {}", ex.getMessage());
        }
    }

    // ── GET /api/payments/export/pdf ─────────────────────────────────────────

    @GetMapping("/export/pdf")
    @PreAuthorize("@rolePermissionService.has('PAYMENT_VIEW')")
    public ResponseEntity<?> exportPdf(@RequestParam(required = false) PaymentStatus status) {
        try {
            List<String[]> rows = reportExportService.paymentRows(status);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            pdfTableExporter.write(buffer, "Payments Report", ReportExportService.PAYMENT_HEADERS, rows, paymentReportMeta(status));
            return ExportHttpUtil.fileResponse(buffer.toByteArray(), MediaType.APPLICATION_PDF, ExportHttpUtil.filename("payments", "pdf"));
        } catch (ReportExportService.ExportNoDataException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exportErrorBody(ex.getMessage(), "EXPORT_NO_DATA"));
        } catch (ReportExportService.ExportTooLargeException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(exportErrorBody(ex.getMessage(), "EXPORT_TOO_LARGE"));
        } catch (Exception ex) {
            log.error("[PAYMENTS_EXPORT] PDF generation failed", ex);
            return ResponseEntity.internalServerError().body(exportErrorBody("Failed to generate the PDF report.", "PDF_GENERATION_FAILED"));
        }
    }

    private Map<String, Object> paymentReportMeta(PaymentStatus status) {
        Long tenantId = TenantContext.getCurrentTenantId();
        String agencyName = tenantRepository.findById(tenantId).map(Tenant::getName).orElse("");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("agencyName", agencyName);
        meta.put("generatedBy", auth != null ? auth.getName() : "system");
        meta.put("generatedAt", java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        meta.put("filters", status != null ? "Status=" + status : "None");
        meta.put("entityLabel", "payments");
        return meta;
    }

    private Map<String, Object> exportErrorBody(String message, String errorCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errorCode", errorCode);
        return body;
    }
}
