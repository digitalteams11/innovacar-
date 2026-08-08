package com.carrental.service;

import com.carrental.dto.invoice.CreateInvoiceRequest;
import com.carrental.dto.invoice.UpdateInvoiceRequest;
import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.dto.invoice.InvoiceExportFilter;
import com.carrental.dto.invoice.MonthlyAccountingSummary;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
    private final ContractRepository contractRepository;
    private final com.carrental.repository.ContractExtensionRepository contractExtensionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

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
     * Monthly accounting rollup — every figure computed from real Invoice/Payment/
     * ContractExtension rows for the caller's tenant, never estimated (see
     * MonthlyAccountingSummary javadoc). "Outstanding" is every not-yet-fully-paid,
     * not-cancelled/refunded invoice's remaining balance; "overdue"/"partially paid" are
     * the same remaining balance narrowed to invoices currently in that specific status,
     * so they're subsets of outstanding, not a separate pool.
     */
    @Transactional(readOnly = true)
    public MonthlyAccountingSummary getMonthlyAccountingSummary(YearMonth month) {
        Long tenantId = TenantContext.getCurrentTenantId();
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<Invoice> invoices = invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(i -> i.getIssueDate() != null && !i.getIssueDate().isBefore(start) && !i.getIssueDate().isAfter(end))
                .toList();

        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        BigDecimal totalPartiallyPaid = BigDecimal.ZERO;
        BigDecimal totalCancelled = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        Set<Long> contractIds = new HashSet<>();

        for (Invoice invoice : invoices) {
            BigDecimal amount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
            BigDecimal collected = paymentService.collectedAmountFor(invoice);
            BigDecimal remaining = amount.subtract(collected).max(BigDecimal.ZERO);

            totalInvoiced = totalInvoiced.add(amount);
            totalCollected = totalCollected.add(collected);
            if (invoice.getContract() != null) contractIds.add(invoice.getContract().getId());

            switch (invoice.getStatus()) {
                case CANCELLED -> totalCancelled = totalCancelled.add(amount);
                case REFUNDED -> totalRefunded = totalRefunded.add(amount);
                case OVERDUE -> {
                    totalOverdue = totalOverdue.add(remaining);
                    totalOutstanding = totalOutstanding.add(remaining);
                }
                case PARTIALLY_PAID -> {
                    totalPartiallyPaid = totalPartiallyPaid.add(remaining);
                    totalOutstanding = totalOutstanding.add(remaining);
                }
                case ISSUED, PENDING, DRAFT -> totalOutstanding = totalOutstanding.add(remaining);
                case PAID -> { /* fully settled — nothing outstanding */ }
            }
        }

        BigDecimal extensionRevenue = contractExtensionRepository
                .findAllByTenantIdAndCreatedAtBetween(tenantId, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .stream()
                .map(ContractExtension::getAdditionalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return MonthlyAccountingSummary.builder()
                .month(month.toString())
                .totalInvoiced(totalInvoiced)
                .totalCollected(totalCollected)
                .totalOutstanding(totalOutstanding)
                .totalOverdue(totalOverdue)
                .totalPartiallyPaid(totalPartiallyPaid)
                .totalCancelled(totalCancelled)
                .totalRefunded(totalRefunded)
                .invoiceCount(invoices.size())
                .contractCount(contractIds.size())
                .rentalRevenue(totalInvoiced.subtract(extensionRevenue).max(BigDecimal.ZERO))
                .extensionRevenue(extensionRevenue)
                .build();
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
        Long tenantId = TenantContext.getCurrentTenantId();

        // Contract-linked mode — see CreateInvoiceRequest javadoc. Idempotent: reuses/
        // updates the existing invoice for this contract if one was already generated
        // (e.g. by ContractService#finalizeContract) rather than creating a duplicate.
        if (request.getContractId() != null) {
            Contract contract = contractRepository.findByIdAndTenantId(request.getContractId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + request.getContractId()));
            return InvoiceResponse.from(syncInvoiceForContract(contract));
        }

        // Manual mode — these fields are only actually required here, not at the DTO
        // validation level, because they're optional/ignored in contract-linked mode.
        if (!StringUtils.hasText(request.getInvoiceNumber())) {
            throw new IllegalArgumentException("Invoice number is required for a manual invoice");
        }
        if (request.getIssueDate() == null) {
            throw new IllegalArgumentException("Issue date is required for a manual invoice");
        }
        if (request.getDueDate() == null) {
            throw new IllegalArgumentException("Due date is required for a manual invoice");
        }
        if (request.getAmount() == null) {
            throw new IllegalArgumentException("Amount is required for a manual invoice");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        Invoice.InvoiceBuilder builder = Invoice.builder()
                .invoiceNumber(request.getInvoiceNumber())
                .issueDate(request.getIssueDate())
                .dueDate(request.getDueDate())
                .amount(request.getAmount())
                .status(InvoiceStatus.ISSUED)
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

        Invoice invoice = builder.build();
        paymentService.updateInvoiceStatus(invoice); // computes status (and persists) from real payments instead of trusting a client-supplied one

        log.info("Created invoice [id={}] '{}' in tenant [{}]",
                invoice.getId(), invoice.getInvoiceNumber(), tenantId);

        return InvoiceResponse.from(invoice);
    }

    /**
     * Idempotent create-or-sync: the single place that keeps a contract's invoice(s) in
     * step with the contract's own current financial state (totalPrice/endDate). Called
     * whenever the contract's total changes for a reason the client didn't directly pay
     * for — activation (ContractService#finalizeContract) and extension
     * (ContractService#extendContract) — never from a raw amount typed into a form.
     *
     * <p>An existing CANCELLED/REFUNDED invoice for this contract is left completely
     * untouched (those are terminal states set by a dedicated cancellation/refund flow,
     * not something a routine contract-total sync should ever revive or alter).
     */
    @Transactional
    public Invoice syncInvoiceForContract(Contract contract) {
        Long tenantId = contract.getTenant().getId();
        BigDecimal total = contract.getTotalPrice() != null ? contract.getTotalPrice() : BigDecimal.ZERO;

        Invoice invoice = invoiceRepository.findAllByTenantIdAndContractId(tenantId, contract.getId())
                .stream()
                .findFirst()
                .orElse(null);

        if (invoice == null) {
            invoice = Invoice.builder()
                    .invoiceNumber(generateInvoiceNumber())
                    .client(contract.getClient())
                    .clientName(contract.getClient() != null ? contract.getClient().getName() : contract.getClientName())
                    .contract(contract)
                    .issueDate(LocalDate.now())
                    .dueDate(contract.getEndDate() != null ? contract.getEndDate() : LocalDate.now())
                    .amount(total)
                    .status(InvoiceStatus.ISSUED)
                    .currency("MAD")
                    .tenant(contract.getTenant())
                    .build();
            log.info("[INVOICE_AUTO_GENERATE] Created invoice for contract [id={}, number={}] tenant=[{}]",
                    contract.getId(), contract.getContractNumber(), tenantId);
        } else if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.REFUNDED) {
            return invoice;
        } else {
            invoice.setAmount(total);
            if (contract.getEndDate() != null) {
                invoice.setDueDate(contract.getEndDate());
            }
        }

        paymentService.updateInvoiceStatus(invoice); // computes status and persists (insert or update)
        return invoice;
    }

    private String generateInvoiceNumber() {
        return String.format("INV-%d-%s",
                Year.now().getValue(),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
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

        Invoice saved;
        if (request.getStatus() != null) {
            // Explicit admin override (e.g. correcting a manual invoice) — respected as-is.
            invoice.setStatus(request.getStatus());
            saved = invoiceRepository.save(invoice);
        } else {
            // No explicit status given — recompute from real payments rather than saving
            // whatever status the row already had, so an amount/date change here can
            // never leave the invoice's status stale (see PaymentService#updateInvoiceStatus).
            paymentService.updateInvoiceStatus(invoice);
            saved = invoice;
        }
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
     * Marks a MANUAL invoice as PAID, recording a matching payment. ADMIN-only.
     *
     * <p>Refused for a contract-linked invoice — its status must only ever move by
     * recording a real {@link Payment} against the contract (PaymentService#recordPayment),
     * never by a direct status flip, so it can never disagree with the contract's own
     * paidAmount (see PaymentService#updateInvoiceStatus).
     *
     * @throws ResourceNotFoundException if the invoice is not found in this tenant
     */
    @Transactional
    public InvoiceResponse markAsPaid(Long id) {
        Invoice invoice = fetchInvoiceInTenant(id);
        if (invoice.getContract() != null) {
            throw new IllegalStateException(
                    "This invoice is linked to a contract — record a payment on the contract instead of marking the invoice paid directly.");
        }
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
