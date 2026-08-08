package com.carrental.service;

import com.carrental.dto.payment.PaymentResponse;
import com.carrental.dto.payment.PaymentStatsResponse;
import com.carrental.dto.payment.RecordPaymentRequest;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Payment-management business logic.
 * Serves as the financial source of truth for the entire platform.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final VehicleRepository vehicleRepository;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;

    // ── READ ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return paymentRepository.findAllByTenantIdOrderByPaymentDateDesc(tenantId)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Payment payment = paymentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getClientPaymentHistory(Long clientId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return paymentRepository.findAllByTenantIdAndClientIdOrderByPaymentDateDesc(tenantId, clientId)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getContractPayments(Long contractId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return paymentRepository.findAllByTenantIdAndContractIdOrderByPaymentDateDesc(tenantId, contractId)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByReservation(Long reservationId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Payment payment = paymentRepository.findByReservationIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException(
                        "Payment not found for reservation: " + reservationId));
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse markAsPaid(Long reservationId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Payment payment = paymentRepository.findByReservationIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException(
                        "Payment not found for reservation: " + reservationId));
        if (payment.getStatus() == com.carrental.entity.PaymentStatus.PAID) {
            throw new IllegalStateException("Payment is already marked as paid");
        }
        payment.setStatus(PaymentStatus.PAID);
        Payment saved = paymentRepository.save(payment);
        syncRelatedBusinessState(saved);
        log.info("Marked payment [id={}] as PAID via reservation [id={}] in tenant [{}]",
                saved.getId(), reservationId, tenantId);
        return PaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PaymentStatsResponse getPaymentStats() {
        Long tenantId = TenantContext.getCurrentTenantId();

        BigDecimal totalRevenue = paymentRepository.sumCollectedRentalRevenueByTenantId(tenantId);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal monthlyRevenue = calculateMonthlyRevenue(tenantId);

        BigDecimal pendingAmount = paymentRepository.sumPendingAmountByTenantId(tenantId);
        long pendingCount = paymentRepository.countPendingByTenantId(tenantId);

        long paidInvoices = invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .count();

        long overdueInvoices = invoiceRepository.findAllByTenantId(tenantId).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
                .count();

        BigDecimal refundAmount = paymentRepository.sumRefundsByTenantId(tenantId);
        long refundCount = paymentRepository.countRefundsByTenantId(tenantId);

        List<PaymentResponse> recent = paymentRepository.findTop5ByTenantIdOrderByPaymentDateDesc(tenantId)
                .stream()
                .map(PaymentResponse::from)
                .toList();

        return PaymentStatsResponse.builder()
                .totalRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .pendingAmount(pendingAmount != null ? pendingAmount : BigDecimal.ZERO)
                .pendingCount(pendingCount)
                .paidInvoices(paidInvoices)
                .overdueInvoices(overdueInvoices)
                .refundAmount(refundAmount != null ? refundAmount : BigDecimal.ZERO)
                .refundCount(refundCount)
                .recentTransactions(recent)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMonthlyRevenue() {
        Long tenantId = TenantContext.getCurrentTenantId();
        Map<YearMonth, BigDecimal> revenueByMonth = paymentRepository.findAllByTenantIdOrderByPaymentDateDesc(tenantId)
                .stream()
                .filter(payment -> payment.getType() == PaymentType.RENTAL)
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID
                        || payment.getStatus() == PaymentStatus.PARTIALLY_PAID)
                .filter(payment -> payment.getPaymentDate() != null)
                .collect(Collectors.groupingBy(
                        payment -> YearMonth.from(payment.getPaymentDate()),
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)
                ));

        return revenueByMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("month", entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                    row.put("year", entry.getKey().getYear());
                    row.put("revenue", entry.getValue());
                    return row;
                })
                .toList();
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Records a new payment and automatically updates all related modules.
     * This is the core financial transaction method.
     */
    @Transactional
    public PaymentResponse recordPayment(RecordPaymentRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        // Idempotency: if the caller supplied a key and a payment already exists under it
        // for this tenant, return that existing payment instead of recording a duplicate —
        // safe for network retries / double-submits. Same unique-constraint-backed pattern
        // as SubscriptionEvent#whopEventId; no key supplied means no dedupe check (existing
        // callers that don't send one yet are unaffected).
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("[PAYMENT_IDEMPOTENT_REPLAY] tenantId={} idempotencyKey={} existingPaymentId={}",
                        tenantId, idempotencyKey, existing.get().getId());
                return PaymentResponse.from(existing.get());
            }
        }

        // Resolve linked entities
        Reservation reservation = null;
        Contract contract = null;
        Invoice invoice = null;
        Client client = null;
        Vehicle vehicle = null;

        if (request.getReservationId() != null) {
            reservation = reservationRepository.findByIdAndTenantId(request.getReservationId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        }
        if (request.getContractId() != null) {
            contract = contractRepository.findByIdAndTenantId(request.getContractId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
        }
        if (request.getInvoiceId() != null) {
            invoice = invoiceRepository.findByIdAndTenantId(request.getInvoiceId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        }
        if (request.getClientId() != null) {
            client = clientRepository.findByIdAndTenantId(request.getClientId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        }
        if (request.getVehicleId() != null) {
            vehicle = vehicleRepository.findByIdAndTenantId(request.getVehicleId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        }

        if (contract == null && reservation != null && reservation.getContract() != null) {
            contract = reservation.getContract();
        }
        if (reservation == null && contract != null && contract.getReservation() != null) {
            reservation = contract.getReservation();
        }
        if (client == null) {
            client = firstNonNull(
                    contract != null ? contract.getClient() : null,
                    reservation != null ? reservation.getClient() : null,
                    invoice != null ? invoice.getClient() : null
            );
        }
        if (vehicle == null) {
            vehicle = firstNonNull(
                    contract != null ? contract.getVehicle() : null,
                    reservation != null ? reservation.getVehicle() : null
            );
        }
        if (invoice == null && contract != null && contract.getInvoiceNumber() != null) {
            invoice = invoiceRepository.findByInvoiceNumberAndTenantId(contract.getInvoiceNumber(), tenantId)
                    .orElse(null);
        }

        PaymentStatus status = determinePaymentStatus(contract, reservation, invoice, request.getAmount(), tenantId);

        PaymentType type = request.getType() != null ? request.getType() : PaymentType.RENTAL;

        Payment payment = Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .amount(request.getAmount())
                .paymentDate(LocalDateTime.now())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CASH)
                .reference(request.getReference())
                .status(status)
                .type(type)
                .reservation(reservation)
                .contract(contract)
                .invoice(invoice)
                .client(client)
                .vehicle(vehicle)
                .notes(request.getNotes())
                .tenant(tenant)
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
                .createdBy(getCurrentUserLabel())
                .createdById(getCurrentUserId())
                .build();

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // A concurrent request recorded the same idempotency key between our existence
            // check and this insert (the unique index is the real guarantee, the check above
            // is just the fast path) — re-fetch and return the winner instead of erroring.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Payment winner = paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                        .orElseThrow(() -> e);
                log.info("[PAYMENT_IDEMPOTENT_RACE] tenantId={} idempotencyKey={} winningPaymentId={}",
                        tenantId, idempotencyKey, winner.getId());
                return PaymentResponse.from(winner);
            }
            throw e;
        }

        syncRelatedBusinessState(saved);
        notifyIfPartiallyPaid(saved.getContract());

        log.info("Recorded payment [id={}, number={}] amount={} for tenant [{}]",
                saved.getId(), saved.getPaymentNumber(), request.getAmount(), tenantId);

        return PaymentResponse.from(saved);
    }

    /**
     * "Payment partiel reçu" — fires only right after a payment leaves the contract
     * PARTIALLY_PAID (never on every payment regardless of outcome, and never on a
     * refund/extension recalculation, both of which also call
     * {@link #recalculateContractFinancials} but aren't "a payment was just received").
     * Real database state only — remaining amount comes straight from the just-recalculated
     * Contract, never a fabricated figure.
     */
    private void notifyIfPartiallyPaid(Contract contract) {
        if (contract == null || !"PARTIALLY_PAID".equals(contract.getPaymentStatus())) {
            return;
        }
        BigDecimal remaining = contract.getRemainingAmount() != null ? contract.getRemainingAmount() : BigDecimal.ZERO;
        notificationService.createNotification(
                "Paiement partiel reçu",
                "Paiement partiel reçu. " + remaining.stripTrailingZeros().toPlainString() + " MAD restant pour le contrat "
                        + contract.getContractNumber() + ".",
                com.carrental.entity.Notification.NotificationType.PAYMENT_PARTIAL,
                contract.getId(),
                contract.getTenant().getId());
    }

    // ── REFUND ────────────────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse refundPayment(Long paymentId, BigDecimal refundAmount, String reason) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Payment payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Only paid or partially paid payments can be refunded");
        }

        BigDecimal alreadyRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal available = payment.getAmount().subtract(alreadyRefunded);

        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        if (refundAmount.compareTo(available) > 0) {
            throw new com.carrental.exception.RefundExceedsAvailableAmountException(refundAmount, available);
        }

        // Never discard the requested amount (the previous behaviour blindly flipped status
        // to REFUNDED regardless of refundAmount) — accumulate it, and only treat the payment
        // as fully REFUNDED once the running total reaches the original amount. A partial
        // refund keeps the payment's existing status so it's still correctly counted as
        // "collected" for the residual (amount - refundedAmount) by every sum query.
        BigDecimal newRefundedTotal = alreadyRefunded.add(refundAmount);
        payment.setRefundedAmount(newRefundedTotal);
        if (newRefundedTotal.compareTo(payment.getAmount()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }
        payment.setNotes((payment.getNotes() != null ? payment.getNotes() + " | " : "")
                + "REFUND: " + refundAmount + " — " + reason);
        Payment saved = paymentRepository.save(payment);

        syncRelatedBusinessState(saved);

        log.info("Refunded payment [id={}] amount={} (totalRefunded={}/{}) in tenant [{}]",
                paymentId, refundAmount, newRefundedTotal, payment.getAmount(), tenantId);
        return PaymentResponse.from(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void syncRelatedBusinessState(Payment payment) {
        if (payment.getContract() != null) {
            recalculateContractFinancials(payment.getContract());
            // Most contract payments carry contract, not invoice, on the Payment row
            // (see recordPayment) — sync any invoice(s) linked to this contract too,
            // not just the one this specific payment happens to reference directly.
            Long contractTenantId = payment.getContract().getTenant().getId();
            for (Invoice contractInvoice : invoiceRepository.findAllByTenantIdAndContractId(
                    contractTenantId, payment.getContract().getId())) {
                updateInvoiceStatus(contractInvoice);
            }
        }
        if (payment.getReservation() != null) {
            updateReservationPaymentStatus(payment.getReservation());
        }
        if (payment.getInvoice() != null) {
            updateInvoiceStatus(payment.getInvoice());
        }
    }

    /**
     * The single authoritative place that derives a contract's paidAmount/
     * remainingAmount/paymentStatus from real {@link Payment} rows — never
     * trust a client-supplied value for these three fields (see
     * ContractService#updateContract, which calls this instead of letting
     * UpdateContractRequest.paidAmount/remainingAmount overwrite the entity
     * directly — that was the exact bug this method exists to prevent:
     * financial totals silently drifting from the actual payment ledger).
     * Package-private on purpose — same package as ContractService, no need
     * for a wider public surface than that one cross-service call needs.
     */
    void recalculateContractFinancials(Contract contract) {
        Long tenantId = contract.getTenant().getId();
        BigDecimal collected = paymentRepository.sumCollectedAmountByTenantIdAndContractId(tenantId, contract.getId());
        if (collected == null) collected = BigDecimal.ZERO;
        BigDecimal totalPrice = contract.getTotalPrice() != null ? contract.getTotalPrice() : BigDecimal.ZERO;
        BigDecimal remaining = totalPrice.subtract(collected).max(BigDecimal.ZERO);

        contract.setPaidAmount(collected);
        contract.setRemainingAmount(remaining);

        if (totalPrice.compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) <= 0) {
            contract.setPaymentStatus("PAID");
        } else if (collected.compareTo(BigDecimal.ZERO) > 0) {
            contract.setPaymentStatus("PARTIALLY_PAID");
        } else {
            contract.setPaymentStatus("PENDING");
        }

        contractRepository.save(contract);
        log.debug("Updated contract [id={}] payment status to {} (paid={}/total={})",
                contract.getId(), contract.getPaymentStatus(), collected, totalPrice);
    }

    private void updateReservationPaymentStatus(Reservation reservation) {
        Long tenantId = reservation.getTenant().getId();
        BigDecimal collected = paymentRepository.sumCollectedAmountByTenantIdAndReservationId(tenantId, reservation.getId());
        if (collected == null) collected = BigDecimal.ZERO;
        BigDecimal totalPrice = reservation.getTotalPrice() != null ? reservation.getTotalPrice() : BigDecimal.ZERO;

        reservation.setPaidAmount(collected);

        if (totalPrice.compareTo(BigDecimal.ZERO) > 0 && collected.compareTo(totalPrice) >= 0) {
            reservation.setPaymentStatus("PAID");
        } else if (collected.compareTo(BigDecimal.ZERO) > 0) {
            reservation.setPaymentStatus("PARTIALLY_PAID");
        } else {
            reservation.setPaymentStatus("PENDING");
        }

        reservationRepository.save(reservation);
        log.debug("Updated reservation [id={}] payment status to {} (paid={}/total={})",
                reservation.getId(), reservation.getPaymentStatus(), collected, totalPrice);
    }

    /**
     * The single authoritative place that derives an invoice's status from real payment
     * records — never settable by the client (see CreateInvoiceRequest, which has no
     * status field at all) and never left to drift from what was actually collected.
     * Package-private so {@link InvoiceService} can call it too (contract-generated
     * invoices go through {@code InvoiceService#syncInvoiceForContract}, manual ones
     * through {@code InvoiceService#createInvoice} — both end up here rather than each
     * having their own copy of this calculation).
     *
     * <p>CANCELLED/REFUNDED are terminal states set only by dedicated cancellation/refund
     * flows (ContractService#cancelContract, a future invoice-refund action) — never
     * overwritten here regardless of what payments say, since "cancelled" and "refunded"
     * describe something that happened to the invoice, not a function of amounts paid.
     *
     * <p>For a contract-linked invoice, "collected" is the contract's own paidAmount —
     * the same {@link Payment} rows {@link #recalculateContractFinancials} already sums —
     * rather than a separate sum of payments carrying this invoice's own FK directly.
     * Most contract payments are recorded against the contract, not a specific invoice
     * (see recordPayment), so summing by invoice_id alone would under-count and let the
     * invoice and the contract it bills silently disagree on how much was paid.
     */
    void updateInvoiceStatus(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.REFUNDED) {
            return;
        }

        BigDecimal collected = collectedAmountFor(invoice);
        BigDecimal invoiceAmount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
        boolean overdue = invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now());

        InvoiceStatus newStatus;
        if (invoiceAmount.compareTo(BigDecimal.ZERO) > 0 && collected.compareTo(invoiceAmount) >= 0) {
            newStatus = InvoiceStatus.PAID;
        } else if (overdue) {
            newStatus = InvoiceStatus.OVERDUE;
        } else if (collected.compareTo(BigDecimal.ZERO) > 0) {
            newStatus = InvoiceStatus.PARTIALLY_PAID;
        } else {
            newStatus = InvoiceStatus.ISSUED;
        }

        invoice.setStatus(newStatus);
        invoiceRepository.save(invoice);
        log.debug("Updated invoice [id={}] status to {} (paid={}/total={})",
                invoice.getId(), invoice.getStatus(), collected, invoiceAmount);
    }

    /**
     * How much of an invoice has actually been collected — the same computation
     * {@link #updateInvoiceStatus} uses, exposed so callers that only need the number
     * (monthly accounting, invoice detail views) don't need their own copy of the
     * contract-linked-vs-manual branch. See {@link #updateInvoiceStatus} javadoc for why
     * a contract-linked invoice reads the contract's paidAmount rather than summing
     * payments by invoice_id directly.
     */
    BigDecimal collectedAmountFor(Invoice invoice) {
        if (invoice.getContract() != null) {
            return invoice.getContract().getPaidAmount() != null
                    ? invoice.getContract().getPaidAmount() : BigDecimal.ZERO;
        }
        Long tenantId = invoice.getTenant().getId();
        BigDecimal collected = paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(tenantId, invoice.getId());
        return collected != null ? collected : BigDecimal.ZERO;
    }

    private BigDecimal calculateMonthlyRevenue(Long tenantId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1);
        BigDecimal revenue = paymentRepository.sumCollectedRentalRevenueBetween(tenantId, startOfMonth, endOfMonth);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    private PaymentStatus determinePaymentStatus(Contract contract, Reservation reservation, Invoice invoice,
                                                 BigDecimal paymentAmount, Long tenantId) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal alreadyCollected = BigDecimal.ZERO;

        if (contract != null) {
            total = contract.getTotalPrice() != null ? contract.getTotalPrice() : BigDecimal.ZERO;
            alreadyCollected = paymentRepository.sumCollectedAmountByTenantIdAndContractId(tenantId, contract.getId());
        } else if (reservation != null) {
            total = reservation.getTotalPrice() != null ? reservation.getTotalPrice() : BigDecimal.ZERO;
            alreadyCollected = paymentRepository.sumCollectedAmountByTenantIdAndReservationId(tenantId, reservation.getId());
        } else if (invoice != null) {
            total = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
            alreadyCollected = paymentRepository.sumCollectedAmountByTenantIdAndInvoiceId(tenantId, invoice.getId());
        }

        if (alreadyCollected == null) alreadyCollected = BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentStatus.PAID;
        }

        return alreadyCollected.add(paymentAmount).compareTo(total) >= 0
                ? PaymentStatus.PAID
                : PaymentStatus.PARTIALLY_PAID;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    /** Same pattern as ContractService#getCurrentUser — best-effort actor label, never blocks the transaction. */
    private String getCurrentUserLabel() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    private Long getCurrentUserId() {
        try {
            var principal = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal();
            return principal instanceof com.carrental.entity.User u ? u.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String generatePaymentNumber() {
        return String.format("PAY-%s-%s",
                Year.now().getValue(),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
    }
}
