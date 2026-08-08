package com.carrental.service;

import com.carrental.dto.invoice.InvoiceResponse;
import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Recovers from a {@code DataIntegrityViolationException} on invoice creation by looking
 * up whichever invoice actually exists, in a brand-new transaction — see
 * InvoiceService#createInvoice for why this has to be a separate bean and a separate
 * transaction rather than a same-transaction retry.
 *
 * <p>Once any statement inside a transaction fails against Postgres, that transaction's
 * underlying connection refuses every further command until it's rolled back (Postgres'
 * "current transaction is aborted" behavior) — this is true regardless of what Spring/JPA
 * think the transaction's Java-level state is. A plain {@code catch (DataIntegrityViolationException)}
 * followed by another query on the SAME connection would itself fail. {@code REQUIRES_NEW}
 * on a method called through a real Spring proxy (a different bean, not a same-class
 * self-call) suspends the broken transaction and opens a fresh connection for the recovery
 * query, which is what actually makes the retry work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceConflictRecoveryService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;

    /**
     * Looks up the contract's current active (not CANCELLED/REFUNDED) invoice — same
     * definition of "active" as InvoiceService#syncInvoiceForContract's own pre-check —
     * and builds the full response DTO for it entirely inside this transaction, so no lazy
     * association on the returned object is ever touched after this method returns (the
     * caller's own transaction is the aborted one; touching a lazy proxy there would fail
     * exactly the same way a direct query would).
     *
     * <p>Empty means the DataIntegrityViolationException that triggered this lookup was
     * NOT the contract-uniqueness constraint — some other constraint is responsible, and
     * the caller should let the original exception (with its own specific
     * GlobalExceptionHandler mapping) surface instead of masking it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<InvoiceResponse> recoverActiveInvoiceForContract(Long tenantId, Long contractId) {
        Optional<Invoice> found = invoiceRepository.findAllByTenantIdAndContractId(tenantId, contractId).stream()
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED && i.getStatus() != InvoiceStatus.REFUNDED)
                .findFirst();

        found.ifPresent(invoice -> log.warn(
                "[INVOICE_CREATE_CONFLICT_RECOVERED] tenantId={} contractId={} existingInvoiceId={} existingInvoiceNumber={}",
                tenantId, contractId, invoice.getId(), invoice.getInvoiceNumber()));

        return found.map(invoice -> {
            BigDecimal paid = paymentService.collectedAmountFor(invoice);
            BigDecimal amount = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
            BigDecimal remaining = amount.subtract(paid).max(BigDecimal.ZERO);
            return InvoiceResponse.from(invoice, paid, remaining);
        });
    }
}
