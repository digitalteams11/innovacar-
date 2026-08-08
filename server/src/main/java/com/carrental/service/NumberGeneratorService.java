package com.carrental.service;

import com.carrental.entity.InvoiceNumberSequence;
import com.carrental.repository.InvoiceNumberSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Locale;
import java.util.UUID;

/**
 * Single place that mints document numbers, so every caller across the
 * codebase agrees on format and concurrency-safety instead of each service
 * having its own (previously: three different payment-number generators and
 * one non-sequential invoice-number generator existed independently).
 */
@Service
@RequiredArgsConstructor
public class NumberGeneratorService {

    private final InvoiceNumberSequenceRepository invoiceNumberSequenceRepository;

    /**
     * Sequential, per-tenant, per-year invoice number: {@code FAC-2026-000001}.
     * Safe under concurrent requests — the (tenant, year) counter row is
     * pessimistically locked for the duration of the caller's transaction, so
     * two simultaneous invoice creations can never be handed the same number
     * (see InvoiceNumberSequenceRepository).
     *
     * <p>Runs in the caller's existing transaction (default propagation) —
     * the lock must be held until the invoice insert that follows this call
     * commits, otherwise a second request could allocate the same number
     * before the first invoice row exists.
     */
    @Transactional
    public String generateInvoiceNumber(Long tenantId) {
        int year = Year.now().getValue();
        invoiceNumberSequenceRepository.ensureRowExists(tenantId, year);
        InvoiceNumberSequence seq = invoiceNumberSequenceRepository.lockForUpdate(tenantId, year)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to allocate an invoice number sequence for tenant " + tenantId));
        long next = seq.getLastNumber() + 1;
        seq.setLastNumber(next);
        invoiceNumberSequenceRepository.save(seq);
        return String.format("FAC-%d-%06d", year, next);
    }

    /**
     * Payment number: {@code PAY-2026-A1B2C3D4}. UUID-suffixed rather than
     * sequential — payments don't need a gap-free legal sequence the way
     * invoices do, and this avoids a second contended counter row on the hot
     * path of recording every single payment.
     */
    public String generatePaymentNumber() {
        return String.format("PAY-%d-%s",
                Year.now().getValue(),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
    }
}
