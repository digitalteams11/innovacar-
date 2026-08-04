package com.carrental.service;

import com.carrental.dto.invoice.CreateInvoiceRequest;
import com.carrental.dto.invoice.UpdateInvoiceRequest;
import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.dto.invoice.InvoiceExportFilter;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.InvoiceRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
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
    private final ClientRepository  clientRepository;
    private final PaymentRepository paymentRepository;

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

    /**
     * Tenant-scoped invoice entity lookup for callers that need the full
     * entity (PDF generation, email dispatch) rather than the DTO — e.g.
     * {@code InvoiceController}'s {@code /pdf} and {@code /email} endpoints.
     * 404s (never 403) on both missing and cross-tenant invoices, exactly
     * like every other lookup in this service.
     */
    @Transactional(readOnly = true)
    public Invoice getInvoiceEntityById(Long id) {
        return fetchInvoiceInTenant(id);
    }

    /**
     * Dynamic-filter invoice export, shared verbatim by the PDF-export and
     * CSV-export controller endpoints so the two formats can never disagree
     * on which invoices matched. Tenant scoping always comes from
     * {@link TenantContext#getCurrentTenantId()} — never from the filter
     * DTO — regardless of anything the frontend sends.
     */
    @Transactional(readOnly = true)
    public List<Invoice> exportFilteredInvoices(InvoiceExportFilter filter) {
        Long tenantId = TenantContext.getCurrentTenantId();
        InvoiceStatus status = null;
        if (filter != null && StringUtils.hasText(filter.getStatus()) && !"all".equalsIgnoreCase(filter.getStatus())) {
            try {
                status = InvoiceStatus.valueOf(filter.getStatus().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Unknown status value — treat as "no status filter" rather than failing the export.
            }
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "issueDate");
        if (filter != null && StringUtils.hasText(filter.getSort())) {
            String[] parts = filter.getSort().split(",", 2);
            String property = parts[0].trim();
            Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            if (StringUtils.hasText(property)) {
                sort = Sort.by(direction, property);
            }
        }

        List<Invoice> results = invoiceRepository.findAllForExport(
                tenantId,
                filter != null && StringUtils.hasText(filter.getSearch()) ? filter.getSearch().trim() : null,
                status,
                filter != null ? filter.getDateFrom() : null,
                filter != null ? filter.getDateTo() : null,
                filter != null ? filter.getClientId() : null,
                filter != null ? filter.getContractId() : null,
                filter != null ? filter.getVehicleId() : null,
                sort);

        if (filter != null && "CURRENT_PAGE".equalsIgnoreCase(filter.getScope())
                && filter.getPageInvoiceIds() != null) {
            var allowedIds = new java.util.HashSet<>(filter.getPageInvoiceIds());
            results = results.stream().filter(inv -> allowedIds.contains(inv.getId())).toList();
        }
        return results;
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

        Invoice.InvoiceBuilder builder = Invoice.builder()
                .invoiceNumber(request.getInvoiceNumber())
                .issueDate(request.getIssueDate())
                .dueDate(request.getDueDate())
                .amount(request.getAmount())
                .status(status)
                .tenant(tenant);

        // Link to client if provided
        if (request.getClientId() != null) {
            Client client = clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + request.getClientId()));
            builder.client(client);
            builder.clientName(client.getName());
        } else if (StringUtils.hasText(request.getClientName())) {
            builder.clientName(request.getClientName());
        }

        Invoice invoice = invoiceRepository.save(builder.build());

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
        if (request.getClientId() != null) {
            Long tenantId = TenantContext.getCurrentTenantId();
            Client client = clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + request.getClientId()));
            invoice.setClient(client);
            invoice.setClientName(client.getName());
        } else if (request.getClientName() != null) {
            invoice.setClientName(request.getClientName().isEmpty() ? null : request.getClientName());
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

        // Auto-create a payment record for this invoice if none exists
        Long tenantId = TenantContext.getCurrentTenantId();
        var existingPayments = paymentRepository.findAllByTenantIdAndInvoiceIdOrderByPaymentDateDesc(tenantId, id);
        if (existingPayments.isEmpty()) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
            Payment payment = Payment.builder()
                    .paymentNumber(generatePaymentNumber())
                    .amount(invoice.getAmount())
                    .paymentDate(java.time.LocalDateTime.now())
                    .paymentMethod(PaymentMethod.CASH)
                    .status(PaymentStatus.PAID)
                    .type(PaymentType.RENTAL)
                    .invoice(invoice)
                    .client(invoice.getClient())
                    .tenant(tenant)
                    .notes("Auto-generated from invoice payment")
                    .build();
            paymentRepository.save(payment);
        }

        log.info("Marked invoice [id={}] as PAID in tenant [{}]",
                id, tenantId);
        return InvoiceResponse.from(saved);
    }

    /**
     * Stamps the PDF-generation metadata (§ "mark previous PDF outdated,
     * regenerate on demand" at the metadata level — no historical PDF
     * archive) after a successful individual PDF render. Called by
     * {@code InvoiceController}'s {@code /pdf} endpoint, never before the
     * PDF bytes are actually confirmed generated.
     */
    @Transactional
    public void markPdfGenerated(Long id, String language) {
        Invoice invoice = fetchInvoiceInTenant(id);
        invoice.setPdfGeneratedAt(java.time.LocalDateTime.now());
        invoice.setPdfLanguage(language);
        invoice.setPdfOutdated(false);
        invoiceRepository.save(invoice);
    }

    private String generatePaymentNumber() {
        String prefix = "PAY";
        String year = String.valueOf(java.time.Year.now().getValue());
        long count = paymentRepository.count() + 1;
        return String.format("%s-%s-%05d", prefix, year, count);
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
