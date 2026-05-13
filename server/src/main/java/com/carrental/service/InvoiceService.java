package com.carrental.service;

import com.carrental.dto.invoice.CreateInvoiceRequest;
import com.carrental.dto.invoice.UpdateInvoiceRequest;
import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.InvoiceRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Invoice-management business logic.
 *
 * <p><strong>Tenant isolation:</strong> every query is scoped to the
 * {@code tenantId} extracted from the JWT via {@link TenantContext}.
 * A user of tenant A will always receive a 404 for invoices that
 * belong to tenant B — preventing both data leakage and enumeration.
 *
 * <p><strong>Access policy (enforced at controller level):</strong>
 * Any authenticated user may read invoices. Only ADMIN users may
 * create, update, delete, or mark them as paid.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final TenantRepository  tenantRepository;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all invoices for the caller's tenant.
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getAllInvoices() {
        Long tenantId = TenantContext.getCurrentTenantId();
        log.debug("Listing invoices for tenant [{}]", tenantId);

        return invoiceRepository.findAllByTenantId(tenantId)
                .stream()
                .map(InvoiceResponse::from)
                .toList();
    }

    /**
     * Fetches a single invoice scoped to the caller's tenant.
     *
     * @throws ResourceNotFoundException if the invoice does not exist in this tenant
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        return InvoiceResponse.from(fetchInvoiceInTenant(id));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Adds a new invoice to the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the tenant record cannot be found
     */
    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest request) {
        Long   tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant   = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        InvoiceStatus status = request.getStatus() != null
                ? request.getStatus()
                : InvoiceStatus.PENDING;

        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .invoiceNumber(request.getInvoiceNumber())
                .clientName(request.getClientName())
                .issueDate(request.getIssueDate())
                .dueDate(request.getDueDate())
                .amount(request.getAmount())
                .status(status)
                .tenant(tenant)
                .build());

        log.info("Created invoice [id={}] '{}' in tenant [{}]",
                invoice.getId(), invoice.getInvoiceNumber(), tenantId);

        return InvoiceResponse.from(invoice);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields in {@code request} are applied.
     * ADMIN-only.
     *
     * @throws ResourceNotFoundException if the invoice is not found in this tenant
     */
    @Transactional
    public InvoiceResponse updateInvoice(Long id, UpdateInvoiceRequest request) {
        Invoice invoice = fetchInvoiceInTenant(id);

        if (StringUtils.hasText(request.getInvoiceNumber())) {
            invoice.setInvoiceNumber(request.getInvoiceNumber());
        }
        if (StringUtils.hasText(request.getClientName())) {
            invoice.setClientName(request.getClientName());
        }
        if (request.getIssueDate() != null) {
            invoice.setIssueDate(request.getIssueDate());
        }
        if (request.getDueDate() != null) {
            invoice.setDueDate(request.getDueDate());
        }
        if (request.getAmount() != null) {
            invoice.setAmount(request.getAmount());
        }
        if (request.getStatus() != null) {
            invoice.setStatus(request.getStatus());
        }

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Updated invoice [id={}] in tenant [{}]", id, TenantContext.getCurrentTenantId());
        return InvoiceResponse.from(saved);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes an invoice from the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the invoice is not found in this tenant
     */
    @Transactional
    public void deleteInvoice(Long id) {
        Invoice invoice = fetchInvoiceInTenant(id);
        invoiceRepository.delete(invoice);
        log.info("Deleted invoice [id={}] from tenant [{}]",
                id, TenantContext.getCurrentTenantId());
    }

    // ── STATUS CHANGE ─────────────────────────────────────────────────────────

    /**
     * Marks an invoice as PAID. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the invoice is not found in this tenant
     */
    @Transactional
    public InvoiceResponse markAsPaid(Long id) {
        Invoice invoice = fetchInvoiceInTenant(id);
        invoice.setStatus(InvoiceStatus.PAID);
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Marked invoice [id={}] as PAID in tenant [{}]",
                id, TenantContext.getCurrentTenantId());
        return InvoiceResponse.from(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tenant-scoped invoice lookup. Returns 404 for both missing and
     * cross-tenant invoices so tenant B cannot discover tenant A's IDs.
     */
    private Invoice fetchInvoiceInTenant(Long invoiceId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice not found with id: " + invoiceId));
    }
}
