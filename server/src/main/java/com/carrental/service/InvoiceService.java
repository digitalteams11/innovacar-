package com.carrental.service;

import com.carrental.dto.invoice.CreateInvoiceRequest;
import com.carrental.dto.invoice.FinancialTimelineEventResponse;
import com.carrental.dto.invoice.InvoiceExportFilter;
import com.carrental.dto.invoice.InvoiceFinancialPreviewResponse;
import com.carrental.dto.invoice.InvoiceLineDto;
import com.carrental.dto.invoice.MonthlyAccountingSummary;
import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.dto.invoice.UpdateInvoiceRequest;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractExtensionRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final ContractExtensionRepository contractExtensionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final NumberGeneratorService numberGeneratorService;

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
                .map(this::toResponseWithFinancials)
                .toList();
    }

    /**
     * Monthly accounting rollup ({@code yyyy-MM} granularity) — thin wrapper
     * over {@link #getAccountingSummary(LocalDate, LocalDate)} for the
     * existing "single month" callers/tests.
     */
    @Transactional(readOnly = true)
    public MonthlyAccountingSummary getMonthlyAccountingSummary(YearMonth month) {
        return getAccountingSummary(month.atDay(1), month.atEndOfMonth());
    }

    /**
     * Accounting rollup over an arbitrary date range (this month / last month /
     * current year / custom range — see InvoiceController) — every figure
     * computed from real Invoice/Payment/ContractExtension rows for the
     * caller's tenant, never estimated. "Outstanding" is every not-yet-fully-
     * paid, not-cancelled/refunded invoice's remaining balance; "overdue"/
     * "partially paid" are the same remaining balance narrowed to invoices
     * currently in that specific status, so they're subsets of outstanding,
     * not a separate pool.
     */
    @Transactional(readOnly = true)
    public MonthlyAccountingSummary getAccountingSummary(LocalDate start, LocalDate end) {
        Long tenantId = TenantContext.getCurrentTenantId();

        List<Invoice> invoices = invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(i -> i.getIssueDate() != null && !i.getIssueDate().isBefore(start) && !i.getIssueDate().isAfter(end))
                .toList();

        BigDecimal totalInvoiced = BigDecimal.ZERO;
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

        // "Collected" is real cash received in this period — a sum of Payment.paymentDate,
        // never derived from which invoices happen to have been issued in the period (that
        // conflates "invoiced" with "collected", the exact confusion the KPI spec forbids —
        // a client can pay in a later month for an invoice issued earlier, and a payment
        // collected this month can settle an invoice issued last month).
        BigDecimal totalCollected = paymentRepository.sumCollectedRentalRevenueBetween(
                tenantId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        if (totalCollected == null) totalCollected = BigDecimal.ZERO;

        BigDecimal extensionRevenue = contractExtensionRepository
                .findAllByTenantIdAndCreatedAtBetween(tenantId, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .stream()
                .map(ContractExtension::getAdditionalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return MonthlyAccountingSummary.builder()
                .month(start.equals(end.withDayOfMonth(1)) || YearMonth.from(start).equals(YearMonth.from(end))
                        ? YearMonth.from(start).toString()
                        : start + " → " + end)
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
        return toResponseWithFinancials(fetchInvoiceInTenant(id));
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

    /**
     * Server-computed financial summary for the "New Invoice" modal's
     * contract-linked mode — contract total, already paid, previously
     * invoiced, and outstanding balance, all read from the real ledger
     * (Contract.paidAmount / the contract's existing active invoice) so the
     * frontend never has to compute or guess these figures itself. Also flags
     * a cancelled contract and an already-fully-paid contract so the UI can
     * warn before a redundant/incorrect invoice is created (never silently
     * blocked here — the decision stays with the user, informed by real data).
     */
    @Transactional(readOnly = true)
    public InvoiceFinancialPreviewResponse getFinancialPreviewForContract(Long contractId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Contract contract = contractRepository.findByIdAndTenantId(contractId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + contractId));

        BigDecimal contractTotal = contract.getTotalPrice() != null ? contract.getTotalPrice() : BigDecimal.ZERO;
        BigDecimal alreadyPaid = contract.getPaidAmount() != null ? contract.getPaidAmount() : BigDecimal.ZERO;

        Invoice activeInvoice = invoiceRepository.findAllByTenantIdAndContractId(tenantId, contractId).stream()
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED && i.getStatus() != InvoiceStatus.REFUNDED)
                .findFirst()
                .orElse(null);

        BigDecimal previouslyInvoiced = activeInvoice != null && activeInvoice.getAmount() != null
                ? activeInvoice.getAmount() : BigDecimal.ZERO;
        BigDecimal outstanding = contractTotal.subtract(alreadyPaid).max(BigDecimal.ZERO);

        return InvoiceFinancialPreviewResponse.builder()
                .contractId(contract.getId())
                .contractNumber(contract.getContractNumber())
                .clientId(contract.getClient() != null ? contract.getClient().getId() : null)
                .clientName(contract.getClient() != null ? contract.getClient().getName() : contract.getClientFullName())
                .vehicleLabel(vehicleLabel(contract))
                .rentalStart(contract.getStartDate())
                .rentalEnd(contract.getEndDate())
                .rentalDays(contract.getRentalDays())
                .contractTotal(contractTotal)
                .alreadyPaid(alreadyPaid)
                .previouslyInvoiced(previouslyInvoiced)
                .outstandingBalance(outstanding)
                .newInvoiceTotal(contractTotal)
                .contractCancelled(contract.getStatus() == ContractStatus.CANCELLED)
                .fullyPaid(contractTotal.compareTo(BigDecimal.ZERO) > 0 && outstanding.compareTo(BigDecimal.ZERO) <= 0)
                .hasActiveInvoice(activeInvoice != null)
                .activeInvoiceId(activeInvoice != null ? activeInvoice.getId() : null)
                .activeInvoiceNumber(activeInvoice != null ? activeInvoice.getInvoiceNumber() : null)
                .activeInvoiceStatus(activeInvoice != null ? activeInvoice.getStatus().name() : null)
                .build();
    }

    private String vehicleLabel(Contract contract) {
        if (contract.getVehicle() == null) return contract.getVehicleRegistration();
        Vehicle v = contract.getVehicle();
        String brand = v.getMarque() != null ? v.getMarque() : "";
        String model = v.getModel() != null ? v.getModel() : "";
        String label = (brand + " " + model).trim();
        return StringUtils.hasText(label) ? label : contract.getVehicleRegistration();
    }

    /**
     * Chronological financial audit trail for a contract — contract creation,
     * every invoice issued, every payment received, extensions, and
     * cancellation, each sourced from its own real table (never fabricated —
     * see class javadoc pattern used throughout this service). Ordered oldest
     * first, matching how an accountant would read it top to bottom.
     */
    @Transactional(readOnly = true)
    public List<FinancialTimelineEventResponse> getFinancialTimeline(Long contractId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Contract contract = contractRepository.findByIdAndTenantId(contractId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + contractId));

        List<FinancialTimelineEventResponse> events = new ArrayList<>();

        if (contract.getCreatedAt() != null) {
            events.add(FinancialTimelineEventResponse.builder()
                    .type("CONTRACT_CREATED")
                    .timestamp(contract.getCreatedAt())
                    .description("Contract " + contract.getContractNumber() + " created")
                    .amount(contract.getTotalPrice())
                    .build());
        }

        for (Invoice invoice : invoiceRepository.findAllByTenantIdAndContractId(tenantId, contractId)) {
            events.add(FinancialTimelineEventResponse.builder()
                    .type("INVOICE_ISSUED")
                    .timestamp(invoice.getIssueDate() != null ? invoice.getIssueDate().atStartOfDay() : null)
                    .description("Invoice " + invoice.getInvoiceNumber() + " issued")
                    .amount(invoice.getAmount())
                    .build());
        }

        for (Payment payment : paymentRepository.findAllByTenantIdAndContractIdOrderByPaymentDateDesc(tenantId, contractId)) {
            BigDecimal refunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : BigDecimal.ZERO;
            events.add(FinancialTimelineEventResponse.builder()
                    .type("PAYMENT_RECEIVED")
                    .timestamp(payment.getPaymentDate())
                    .description("Payment " + payment.getPaymentNumber() + " (" + payment.getPaymentMethod() + ")")
                    .amount(payment.getAmount())
                    .performedBy(payment.getCreatedBy())
                    .build());
            if (refunded.compareTo(BigDecimal.ZERO) > 0) {
                events.add(FinancialTimelineEventResponse.builder()
                        .type("REFUND")
                        .timestamp(payment.getPaymentDate())
                        .description("Refund on payment " + payment.getPaymentNumber())
                        .amount(refunded)
                        .build());
            }
        }

        for (ContractExtension extension : contractExtensionRepository
                .findAllByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId, contractId)) {
            events.add(FinancialTimelineEventResponse.builder()
                    .type("EXTENSION")
                    .timestamp(extension.getCreatedAt())
                    .description("Rental extended by " + extension.getAdditionalDays() + " day(s) to " + extension.getNewEndDate())
                    .amount(extension.getAdditionalAmount())
                    .performedBy(extension.getPerformedBy())
                    .build());
        }

        if (contract.getStatus() == ContractStatus.CANCELLED) {
            events.add(FinancialTimelineEventResponse.builder()
                    .type("CANCELLATION")
                    .timestamp(contract.getCancelledAt())
                    .description(StringUtils.hasText(contract.getCancellationReason())
                            ? "Contract cancelled: " + contract.getCancellationReason()
                            : "Contract cancelled")
                    .performedBy(contract.getCancelledBy())
                    .build());
        }

        return events.stream()
                .sorted(Comparator.comparing(FinancialTimelineEventResponse::getTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
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
            if (contract.getStatus() == ContractStatus.CANCELLED) {
                throw new IllegalStateException("Cannot generate a normal invoice for a cancelled contract.");
            }
            Invoice invoice = syncInvoiceForContract(contract);
            return toResponseWithFinancials(invoice);
        }

        // Manual mode — the exceptional/ad-hoc case. Client and dates must be supplied;
        // amount is always computed server-side from lines (or the legacy flat amount).
        if (request.getIssueDate() == null) {
            throw new IllegalArgumentException("Issue date is required for a manual invoice");
        }
        if (request.getDueDate() == null) {
            throw new IllegalArgumentException("Due date is required for a manual invoice");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        Invoice.InvoiceBuilder builder = Invoice.builder()
                .invoiceNumber(numberGeneratorService.generateInvoiceNumber(tenantId))
                .issueDate(request.getIssueDate())
                .dueDate(request.getDueDate())
                .status(InvoiceStatus.ISSUED)
                .sourceType(InvoiceSourceType.MANUAL)
                .currency("MAD")
                .notes(request.getNotes())
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

        List<InvoiceLine> lines = buildManualLines(invoice, request);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A manual invoice requires either line items or an amount");
        }
        applyLinesAndTotals(invoice, lines, request.getDiscountAmount(), request.getTaxAmount());

        paymentService.updateInvoiceStatus(invoice); // computes status (and persists) from real payments instead of trusting a client-supplied one

        log.info("Created manual invoice [id={}] '{}' in tenant [{}]",
                invoice.getId(), invoice.getInvoiceNumber(), tenantId);

        return toResponseWithFinancials(invoice);
    }

    /** Builds line entities for a manual invoice — {@code lines} preferred, flat {@code amount} as fallback. */
    private List<InvoiceLine> buildManualLines(Invoice invoice, CreateInvoiceRequest request) {
        List<InvoiceLine> lines = new ArrayList<>();
        int order = 0;
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            for (InvoiceLineDto dto : request.getLines()) {
                InvoiceLineType type = parseLineType(dto.getType());
                BigDecimal quantity = dto.getQuantity() != null ? dto.getQuantity() : BigDecimal.ONE;
                BigDecimal unitPrice = dto.getUnitPrice() != null ? dto.getUnitPrice() : BigDecimal.ZERO;
                lines.add(InvoiceLine.builder()
                        .invoice(invoice)
                        .type(type)
                        .description(dto.getDescription())
                        .quantity(quantity)
                        .unitPrice(unitPrice)
                        .total(quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP))
                        .sortOrder(order++)
                        .build());
            }
        } else if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(InvoiceLine.builder()
                    .invoice(invoice)
                    .type(InvoiceLineType.OTHER)
                    .description("Manual invoice")
                    .quantity(BigDecimal.ONE)
                    .unitPrice(request.getAmount())
                    .total(request.getAmount())
                    .sortOrder(0)
                    .build());
        }
        return lines;
    }

    private InvoiceLineType parseLineType(String value) {
        if (!StringUtils.hasText(value)) return InvoiceLineType.OTHER;
        try {
            return InvoiceLineType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return InvoiceLineType.OTHER;
        }
    }

    /** Replaces {@code invoice}'s lines and recomputes subtotal/discount/tax/amount from them — never trusts a client-supplied total. */
    private void applyLinesAndTotals(Invoice invoice, List<InvoiceLine> lines, BigDecimal discount, BigDecimal tax) {
        invoice.getLines().clear();
        invoice.getLines().addAll(lines);

        BigDecimal subtotal = lines.stream().map(InvoiceLine::getTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = discount != null ? discount : BigDecimal.ZERO;
        BigDecimal taxAmount = tax != null ? tax : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discountAmount).add(taxAmount).max(BigDecimal.ZERO);

        invoice.setSubtotalAmount(subtotal);
        invoice.setDiscountAmount(discountAmount);
        invoice.setTaxAmount(taxAmount);
        invoice.setAmount(total);
    }

    /**
     * Idempotent create-or-sync: the single place that keeps a contract's invoice(s) in
     * step with the contract's own current financial state (totalPrice/endDate). Called
     * whenever the contract's total changes for a reason the client didn't directly pay
     * for — activation (ContractService#finalizeContract), extension
     * (ContractService#extendContract), and vehicle return
     * (ContractService#processReturnInspection) — never from a raw amount typed into a form.
     *
     * <p>An existing CANCELLED/REFUNDED invoice for this contract is left completely
     * untouched (those are terminal states set by a dedicated cancellation/refund flow,
     * not something a routine contract-total sync should ever revive or alter). The
     * database also enforces at most one active invoice per contract (see
     * {@code uq_invoice_tenant_contract_active}, migration V100) — this method is the
     * only writer that can violate it, and it never does, since it always reuses an
     * existing active invoice rather than inserting a second one.
     */
    @Transactional
    public Invoice syncInvoiceForContract(Contract contract) {
        Long tenantId = contract.getTenant().getId();
        BigDecimal total = contract.getTotalPrice() != null ? contract.getTotalPrice() : BigDecimal.ZERO;

        Invoice invoice = invoiceRepository.findAllByTenantIdAndContractId(tenantId, contract.getId())
                .stream()
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED && i.getStatus() != InvoiceStatus.REFUNDED)
                .findFirst()
                .orElse(null);

        if (invoice == null) {
            invoice = Invoice.builder()
                    .invoiceNumber(numberGeneratorService.generateInvoiceNumber(tenantId))
                    .client(contract.getClient())
                    .clientName(contract.getClient() != null ? contract.getClient().getName() : contract.getClientName())
                    .contract(contract)
                    .issueDate(LocalDate.now())
                    .dueDate(contract.getEndDate() != null ? contract.getEndDate() : LocalDate.now())
                    .status(InvoiceStatus.ISSUED)
                    .sourceType(InvoiceSourceType.CONTRACT_LINKED)
                    .currency("MAD")
                    .tenant(contract.getTenant())
                    .build();
            log.info("[INVOICE_AUTO_GENERATE] Created invoice for contract [id={}, number={}] tenant=[{}]",
                    contract.getId(), contract.getContractNumber(), tenantId);
        } else if (contract.getEndDate() != null) {
            invoice.setDueDate(contract.getEndDate());
        }

        List<InvoiceLine> lines = buildLinesForContract(invoice, contract, total);
        applyLinesAndTotals(invoice, lines, null, null);
        // A contract-linked invoice's total is the contract's own totalPrice, exactly —
        // applyLinesAndTotals derives it from the lines, which are built to sum to `total`
        // by construction (see buildLinesForContract), so this is a defensive equality
        // rather than a second source of truth.
        invoice.setAmount(total);

        paymentService.updateInvoiceStatus(invoice); // computes status and persists (insert or update)
        return invoice;
    }

    /**
     * Itemized breakdown for a contract-linked invoice: extensions and known
     * fees (delivery/fuel/return/late/cleaning/discount) are each their own
     * line, sourced directly from the contract/its extensions; the RENTAL
     * line absorbs the base rent plus anything not individually itemized
     * (e.g. a manually overridden {@code totalPrice}), so the lines always
     * sum to exactly {@code total} — the contract's own {@code totalPrice}
     * stays the single source of truth for the amount, this is only ever a
     * presentation breakdown of it.
     */
    private List<InvoiceLine> buildLinesForContract(Invoice invoice, Contract contract, BigDecimal total) {
        List<InvoiceLine> lines = new ArrayList<>();
        int order = 1; // 0 reserved for the RENTAL line, added last (needs the remainder)
        BigDecimal accounted = BigDecimal.ZERO;
        Long tenantId = contract.getTenant().getId();

        for (ContractExtension extension : contractExtensionRepository
                .findAllByTenantIdAndContractIdOrderByCreatedAtAsc(tenantId, contract.getId())) {
            BigDecimal amount = extension.getAdditionalAmount() != null ? extension.getAdditionalAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) == 0) continue;
            lines.add(feeLine(invoice, InvoiceLineType.EXTENSION,
                    "Extension +" + extension.getAdditionalDays() + "j (" + extension.getNewEndDate() + ")",
                    BigDecimal.valueOf(extension.getAdditionalDays()), amount, order++));
            accounted = accounted.add(amount);
        }

        accounted = accounted.add(addFeeLineIfPresent(lines, invoice, InvoiceLineType.DELIVERY, "Livraison", contract.getDeliveryFees(), order++));
        accounted = accounted.add(addFeeLineIfPresent(lines, invoice, InvoiceLineType.FUEL, "Carburant", contract.getFuelCharges(), order++));
        accounted = accounted.add(addFeeLineIfPresent(lines, invoice, InvoiceLineType.LATE_RETURN, "Retard", contract.getLateFees(), order++));
        accounted = accounted.add(addFeeLineIfPresent(lines, invoice, InvoiceLineType.CLEANING, "Nettoyage", contract.getCleaningFees(), order++));
        accounted = accounted.add(addFeeLineIfPresent(lines, invoice, InvoiceLineType.DAMAGE, "Retour / dommages", contract.getReturnFees(), order++));

        BigDecimal discount = contract.getDiscountAmount() != null ? contract.getDiscountAmount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(feeLine(invoice, InvoiceLineType.DISCOUNT, "Remise", BigDecimal.ONE, discount.negate(), order++));
            accounted = accounted.subtract(discount);
        }

        BigDecimal rentalAmount = total.subtract(accounted);
        Integer days = contract.getRentalDays();
        BigDecimal quantity = days != null && days > 0 ? BigDecimal.valueOf(days) : BigDecimal.ONE;
        BigDecimal unitPrice = contract.getDailyPrice() != null ? contract.getDailyPrice()
                : rentalAmount.divide(quantity, 2, RoundingMode.HALF_UP);
        lines.add(0, InvoiceLine.builder()
                .invoice(invoice)
                .type(InvoiceLineType.RENTAL)
                .description("Location" + (days != null ? " (" + days + "j)" : ""))
                .quantity(quantity)
                .unitPrice(unitPrice)
                .total(rentalAmount)
                .sortOrder(0)
                .build());

        return lines;
    }

    private BigDecimal addFeeLineIfPresent(List<InvoiceLine> lines, Invoice invoice, InvoiceLineType type,
                                            String description, BigDecimal amount, int order) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        lines.add(feeLine(invoice, type, description, BigDecimal.ONE, amount, order));
        return amount;
    }

    private InvoiceLine feeLine(Invoice invoice, InvoiceLineType type, String description,
                                 BigDecimal quantity, BigDecimal amount, int order) {
        return InvoiceLine.builder()
                .invoice(invoice)
                .type(type)
                .description(description)
                .quantity(quantity)
                .unitPrice(quantity.compareTo(BigDecimal.ZERO) != 0
                        ? amount.divide(quantity, 2, RoundingMode.HALF_UP) : amount)
                .total(amount)
                .sortOrder(order)
                .build();
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields in {@code request} are applied.
     * ADMIN-only. Does not touch line items (see CreateInvoiceRequest for the
     * itemized creation path) — a direct amount edit here is the same
     * "correcting a manual invoice" escape hatch that existed before line
     * items were introduced.
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
            invoice.setSubtotalAmount(request.getAmount());
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
        return toResponseWithFinancials(saved);
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
                    .paymentNumber(numberGeneratorService.generatePaymentNumber())
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
        return toResponseWithFinancials(saved);
    }

    /**
     * Cancels an invoice — never a delete (financial history is preserved, see class
     * javadoc pattern). A PAID invoice must be refunded instead (see PaymentService#refundPayment) —
     * cancellation is for an invoice that was never actually settled, not a reversal of
     * money already collected. Already-CANCELLED/REFUNDED is a no-op error, not silently
     * re-applied, so a stale double-click surfaces rather than overwriting the original
     * cancellation's reason/actor.
     *
     * @throws ResourceNotFoundException if the invoice is not found in this tenant
     */
    @Transactional
    public InvoiceResponse cancelInvoice(Long id, String reason) {
        Invoice invoice = fetchInvoiceInTenant(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("This invoice is already cancelled.");
        }
        if (invoice.getStatus() == InvoiceStatus.REFUNDED) {
            throw new IllegalStateException("This invoice has already been refunded.");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException(
                    "A fully paid invoice cannot be cancelled directly — refund the underlying payment(s) instead.");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancellationReason(reason);
        invoice.setCancelledBy(currentUserLabel());
        invoice.setCancelledById(currentUserId());
        invoice.setCancelledAt(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);

        log.info("[INVOICE_CANCEL] invoiceId={} number={} tenantId={} reason={}",
                id, saved.getInvoiceNumber(), TenantContext.getCurrentTenantId(), reason);
        return toResponseWithFinancials(saved);
    }

    /** Same best-effort actor pattern as ContractService#getCurrentUser / PaymentService#getCurrentUserLabel. */
    private String currentUserLabel() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    private Long currentUserId() {
        try {
            var principal = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal();
            return principal instanceof com.carrental.entity.User u ? u.getId() : null;
        } catch (Exception e) {
            return null;
        }
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
        invoice.setPdfGeneratedAt(LocalDateTime.now());
        invoice.setPdfLanguage(language);
        invoice.setPdfOutdated(false);
        invoiceRepository.save(invoice);
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

    private InvoiceResponse toResponseWithFinancials(Invoice invoice) {
        BigDecimal paid = paymentService.collectedAmountFor(invoice);
        BigDecimal amount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
        BigDecimal remaining = amount.subtract(paid).max(BigDecimal.ZERO);
        return InvoiceResponse.from(invoice, paid, remaining);
    }
}
