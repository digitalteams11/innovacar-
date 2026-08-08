package com.carrental.service;

import com.carrental.entity.InvoiceNumberSequence;
import com.carrental.repository.InvoiceNumberSequenceRepository;
import com.carrental.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the exact bug reported against CTR-2026-00005: POST /api/invoices kept
 * returning 409 on every retry because the (tenant, year) counter and the real
 * invoices.invoice_number values had drifted — the counter kept reproducing the
 * same already-taken number, and since the failed insert rolled back the
 * counter increment too, the drift never resolved itself. generateInvoiceNumber
 * must now detect that and skip forward past the collision instead of handing
 * out the same doomed number forever.
 */
@ExtendWith(MockitoExtension.class)
class NumberGeneratorServiceTest {

    @Mock private InvoiceNumberSequenceRepository invoiceNumberSequenceRepository;
    @Mock private InvoiceRepository invoiceRepository;

    private NumberGeneratorService newService() {
        return new NumberGeneratorService(invoiceNumberSequenceRepository, invoiceRepository);
    }

    @Test
    void generateInvoiceNumberReturnsTheNextSequentialNumberWhenNoDriftExists() {
        int year = Year.now().getValue();
        InvoiceNumberSequence seq = InvoiceNumberSequence.builder().tenantId(1L).year(year).lastNumber(4L).build();
        when(invoiceNumberSequenceRepository.lockForUpdate(1L, year)).thenReturn(Optional.of(seq));
        when(invoiceRepository.existsByInvoiceNumberAndTenantId(any(), eq(1L))).thenReturn(false);

        String number = newService().generateInvoiceNumber(1L);

        assertThat(number).isEqualTo(String.format("FAC-%d-000005", year));
        assertThat(seq.getLastNumber()).isEqualTo(5L);
        verify(invoiceNumberSequenceRepository).save(seq);
    }

    @Test
    void generateInvoiceNumberSkipsPastAnAlreadyTakenNumberInsteadOfReturningItForever() {
        int year = Year.now().getValue();
        InvoiceNumberSequence seq = InvoiceNumberSequence.builder().tenantId(1L).year(year).lastNumber(0L).build();
        when(invoiceNumberSequenceRepository.lockForUpdate(1L, year)).thenReturn(Optional.of(seq));
        String taken = String.format("FAC-%d-000001", year);
        String taken2 = String.format("FAC-%d-000002", year);
        String free = String.format("FAC-%d-000003", year);
        when(invoiceRepository.existsByInvoiceNumberAndTenantId(taken, 1L)).thenReturn(true);
        when(invoiceRepository.existsByInvoiceNumberAndTenantId(taken2, 1L)).thenReturn(true);
        when(invoiceRepository.existsByInvoiceNumberAndTenantId(free, 1L)).thenReturn(false);

        String number = newService().generateInvoiceNumber(1L);

        assertThat(number).isEqualTo(free);
        assertThat(seq.getLastNumber()).isEqualTo(3L); // counter permanently advanced past the drift
    }

    @Test
    void generateInvoiceNumberCreatesTheCounterRowIfItDoesNotExistYet() {
        int year = Year.now().getValue();
        InvoiceNumberSequence seq = InvoiceNumberSequence.builder().tenantId(2L).year(year).lastNumber(0L).build();
        when(invoiceNumberSequenceRepository.lockForUpdate(2L, year)).thenReturn(Optional.of(seq));
        when(invoiceRepository.existsByInvoiceNumberAndTenantId(any(), eq(2L))).thenReturn(false);

        newService().generateInvoiceNumber(2L);

        verify(invoiceNumberSequenceRepository).ensureRowExists(2L, year);
    }
}
