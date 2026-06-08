package com.carrental.controller;

import com.carrental.dto.invoice.CreateInvoiceRequest;
import com.carrental.dto.invoice.UpdateInvoiceRequest;
import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Invoice-management REST controller.
 *
 * <pre>
 * GET    /api/invoices              – list all invoices           [authenticated]
 * GET    /api/invoices/{id}         – get invoice by id           [authenticated]
 * POST   /api/invoices              – create invoice              [ADMIN]
 * PUT    /api/invoices/{id}         – partial update              [ADMIN]
 * PATCH  /api/invoices/{id}/pay     – mark invoice as paid        [ADMIN]
 * DELETE /api/invoices/{id}         – delete invoice              [ADMIN]
 * </pre>
 *
 * All endpoints sit behind the {@code JwtAuthenticationFilter} — an invalid or
 * missing token will never reach the controller.
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    // ── GET /api/invoices ────────────────────────────────────────────────────

    /**
     * Returns all invoices in the caller's tenant.
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> listInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    // ── GET /api/invoices/{id} ───────────────────────────────────────────────

    /**
     * Fetches a single invoice. Returns 404 for invoices belonging to other
     * tenants (prevents cross-tenant enumeration).
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    // ── POST /api/invoices ───────────────────────────────────────────────────

    /**
     * Registers a new invoice in the caller's tenant. ADMIN-only.
     */
    @PostMapping
    @PreAuthorize("@rolePermissionService.has('MANAGE_INVOICES')")
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceService.createInvoice(request));
    }

    // ── PUT /api/invoices/{id} ───────────────────────────────────────────────

    /**
     * Partially updates an invoice. Only non-null fields are applied.
     * ADMIN-only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_INVOICES')")
    public ResponseEntity<InvoiceResponse> updateInvoice(
            @PathVariable Long id,
            @Valid @RequestBody UpdateInvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.updateInvoice(id, request));
    }

    // ── PATCH /api/invoices/{id}/pay ─────────────────────────────────────────

    /**
     * Marks an invoice as PAID. ADMIN-only.
     */
    @PatchMapping("/{id}/pay")
    @PreAuthorize("@rolePermissionService.has('MANAGE_INVOICES')")
    public ResponseEntity<InvoiceResponse> markAsPaid(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.markAsPaid(id));
    }

    // ── DELETE /api/invoices/{id} ────────────────────────────────────────────

    /**
     * Hard-deletes an invoice. ADMIN-only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_INVOICES')")
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
