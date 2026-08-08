package com.carrental.service;

import com.carrental.dto.payment.PaymentResponse;
import com.carrental.dto.payment.RecordPaymentRequest;
import com.carrental.entity.Payment;
import com.carrental.entity.PaymentMethod;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.PaymentType;
import com.carrental.entity.Reservation;
import com.carrental.entity.Tenant;
import com.carrental.exception.RefundExceedsAvailableAmountException;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.InvoiceRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private NotificationService notificationService;
    @Mock private NumberGeneratorService numberGeneratorService;

    @InjectMocks
    private PaymentService paymentService;

    private static final Long TENANT_ID = 1L;
    private static final Long RESERVATION_ID = 100L;
    private static final Long PAYMENT_ID = 500L;

    private Payment payment;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);

        Tenant tenant = Tenant.builder().id(TENANT_ID).build();
        Reservation reservation = Reservation.builder().id(RESERVATION_ID).tenant(tenant).build();

        payment = Payment.builder()
                .id(PAYMENT_ID)
                .paymentNumber("RES-" + RESERVATION_ID)
                .reservation(reservation)
                .amount(new BigDecimal("150.00"))
                .paymentDate(java.time.LocalDateTime.now())
                .paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PENDING)
                .type(PaymentType.RENTAL)
                .tenant(tenant)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getPaymentByReservation_success() {
        when(paymentRepository.findByReservationIdAndTenantId(RESERVATION_ID, TENANT_ID))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByReservation(RESERVATION_ID);

        assertThat(response.getId()).isEqualTo(PAYMENT_ID);
        assertThat(response.getAmount()).isEqualByComparingTo("150.00");
        assertThat(response.isPaid()).isFalse();
    }

    @Test
    void getPaymentByReservation_throws404IfNotFound() {
        when(paymentRepository.findByReservationIdAndTenantId(RESERVATION_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByReservation(RESERVATION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAsPaid_success() {
        when(paymentRepository.findByReservationIdAndTenantId(RESERVATION_ID, TENANT_ID))
                .thenReturn(Optional.of(payment));
                
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.sumCollectedAmountByTenantIdAndReservationId(TENANT_ID, RESERVATION_ID))
                .thenReturn(new BigDecimal("150.00"));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.markAsPaid(RESERVATION_ID);

        assertThat(response.isPaid()).isTrue();
        verify(paymentRepository).save(payment);
    }

    @Test
    void markAsPaid_failsIfAlreadyPaid() {
        payment.setStatus(PaymentStatus.PAID);
        when(paymentRepository.findByReservationIdAndTenantId(RESERVATION_ID, TENANT_ID))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.markAsPaid(RESERVATION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already marked as paid");
                
        verify(paymentRepository, never()).save(any());
    }

    // ── refundPayment: partial refunds must be tracked, not discarded ──────────

    @Test
    void refundPayment_partialRefundKeepsStatusAndAccumulatesRefundedAmount() {
        payment.setStatus(PaymentStatus.PAID);
        payment.setRefundedAmount(BigDecimal.ZERO);
        when(paymentRepository.findByIdAndTenantId(PAYMENT_ID, TENANT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.sumCollectedAmountByTenantIdAndReservationId(TENANT_ID, RESERVATION_ID))
                .thenReturn(new BigDecimal("100.00"));

        PaymentResponse response = paymentService.refundPayment(PAYMENT_ID, new BigDecimal("50.00"), "Client cancelled 1 day");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("50.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refundPayment_fullRefundFlipsStatusToRefunded() {
        payment.setStatus(PaymentStatus.PAID);
        payment.setRefundedAmount(BigDecimal.ZERO);
        when(paymentRepository.findByIdAndTenantId(PAYMENT_ID, TENANT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.sumCollectedAmountByTenantIdAndReservationId(TENANT_ID, RESERVATION_ID))
                .thenReturn(BigDecimal.ZERO);

        PaymentResponse response = paymentService.refundPayment(PAYMENT_ID, new BigDecimal("150.00"), "Full cancellation");

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void refundPayment_rejectsAmountExceedingWhatsLeftToRefund() {
        payment.setStatus(PaymentStatus.PAID);
        payment.setRefundedAmount(new BigDecimal("100.00")); // only 50.00 left available
        when(paymentRepository.findByIdAndTenantId(PAYMENT_ID, TENANT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(PAYMENT_ID, new BigDecimal("60.00"), "Too much"))
                .isInstanceOf(RefundExceedsAvailableAmountException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundPayment_rejectsZeroOrNegativeAmount() {
        payment.setStatus(PaymentStatus.PAID);
        when(paymentRepository.findByIdAndTenantId(PAYMENT_ID, TENANT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refundPayment(PAYMENT_ID, BigDecimal.ZERO, "Bad request"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(paymentRepository, never()).save(any());
    }

    // ── recordPayment: idempotency ──────────────────────────────────────────────

    @Test
    void recordPayment_returnsExistingPaymentWhenIdempotencyKeyAlreadyUsed() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(paymentRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, "retry-key-1"))
                .thenReturn(Optional.of(payment));

        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("150.00"));
        request.setIdempotencyKey("retry-key-1");

        PaymentResponse response = paymentService.recordPayment(request);

        assertThat(response.getId()).isEqualTo(PAYMENT_ID);
        verify(paymentRepository, never()).save(any());
    }

    // ── recordPayment: overpayment guard ────────────────────────────────────────

    @Test
    void recordPayment_rejectsRentalPaymentThatWouldExceedContractTotal() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).build();
        com.carrental.entity.Contract contract = com.carrental.entity.Contract.builder()
                .id(700L).tenant(tenant).totalPrice(new BigDecimal("3000.00")).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(contractRepository.findByIdAndTenantId(700L, TENANT_ID)).thenReturn(Optional.of(contract));
        when(paymentRepository.sumCollectedAmountByTenantIdAndContractId(TENANT_ID, 700L))
                .thenReturn(new BigDecimal("2999.00"));

        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("5.00")); // 2999 + 5 = 3004 > 3000 total
        request.setContractId(700L);

        assertThatThrownBy(() -> paymentService.recordPayment(request))
                .isInstanceOf(com.carrental.exception.PaidAmountExceedsTotalException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void recordPayment_allowsRentalPaymentThatExactlyCoversRemainingBalance() {
        Tenant tenant = Tenant.builder().id(TENANT_ID).build();
        com.carrental.entity.Contract contract = com.carrental.entity.Contract.builder()
                .id(701L).tenant(tenant).totalPrice(new BigDecimal("3000.00")).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(contractRepository.findByIdAndTenantId(701L, TENANT_ID)).thenReturn(Optional.of(contract));
        when(paymentRepository.sumCollectedAmountByTenantIdAndContractId(TENANT_ID, 701L))
                .thenReturn(new BigDecimal("2000.00"));
        when(numberGeneratorService.generatePaymentNumber()).thenReturn("PAY-2026-TEST0001");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepository.findAllByTenantIdAndContractId(TENANT_ID, 701L)).thenReturn(java.util.List.of());

        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("1000.00")); // exactly the remaining balance
        request.setContractId(701L);

        PaymentResponse response = paymentService.recordPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void recordPayment_overpaymentGuardDoesNotApplyToNonRentalPaymentTypes() {
        // A DEPOSIT/caution payment is not revenue capped by the contract's rental total —
        // e.g. a 3000 MAD deposit on a 1500 MAD rental is a normal, valid case.
        Tenant tenant = Tenant.builder().id(TENANT_ID).build();
        com.carrental.entity.Contract contract = com.carrental.entity.Contract.builder()
                .id(702L).tenant(tenant).totalPrice(new BigDecimal("1500.00")).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(contractRepository.findByIdAndTenantId(702L, TENANT_ID)).thenReturn(Optional.of(contract));
        when(numberGeneratorService.generatePaymentNumber()).thenReturn("PAY-2026-TEST0002");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepository.findAllByTenantIdAndContractId(TENANT_ID, 702L)).thenReturn(java.util.List.of());

        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("3000.00"));
        request.setContractId(702L);
        request.setType(PaymentType.DEPOSIT_PAYMENT);

        // Must not throw despite 3000 > contract total of 1500 — the guard is scoped to RENTAL only.
        paymentService.recordPayment(request);

        verify(paymentRepository).save(any(Payment.class));
        // Called by determinePaymentStatus + recalculateContractFinancials (both run for
        // every payment type) but NOT a third time by the overpayment guard, which is
        // skipped for this type — i.e. no exception was thrown despite 3000 > 1500.
        verify(paymentRepository, times(2)).sumCollectedAmountByTenantIdAndContractId(TENANT_ID, 702L);
    }

    // ── updateInvoiceStatus: contract-linked invoices read the contract's own paidAmount ──

    @Test
    void updateInvoiceStatus_contractLinkedInvoiceBecomesPartiallyPaidFromContractPaidAmount() {
        com.carrental.entity.Tenant tenant = com.carrental.entity.Tenant.builder().id(TENANT_ID).build();
        com.carrental.entity.Contract contract = com.carrental.entity.Contract.builder()
                .id(300L).tenant(tenant)
                .totalPrice(new BigDecimal("7000.00"))
                .paidAmount(new BigDecimal("2000.00")) // already recalculated by recalculateContractFinancials
                .build();
        com.carrental.entity.Invoice invoice = com.carrental.entity.Invoice.builder()
                .id(400L).tenant(tenant).contract(contract)
                .amount(new BigDecimal("7000.00"))
                .dueDate(java.time.LocalDate.now().plusDays(10))
                .status(com.carrental.entity.InvoiceStatus.ISSUED)
                .build();
        when(invoiceRepository.save(any(com.carrental.entity.Invoice.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.updateInvoiceStatus(invoice);

        assertThat(invoice.getStatus()).isEqualTo(com.carrental.entity.InvoiceStatus.PARTIALLY_PAID);
        // Never queries payments by invoice_id directly for a contract-linked invoice —
        // the contract's own paidAmount is the source of truth (see method javadoc).
        verify(paymentRepository, never()).sumCollectedAmountByTenantIdAndInvoiceId(anyLong(), anyLong());
    }

    @Test
    void updateInvoiceStatus_contractLinkedInvoiceBecomesPaidWhenFullyCollected() {
        com.carrental.entity.Tenant tenant = com.carrental.entity.Tenant.builder().id(TENANT_ID).build();
        com.carrental.entity.Contract contract = com.carrental.entity.Contract.builder()
                .id(301L).tenant(tenant)
                .totalPrice(new BigDecimal("5000.00"))
                .paidAmount(new BigDecimal("5000.00"))
                .build();
        com.carrental.entity.Invoice invoice = com.carrental.entity.Invoice.builder()
                .id(401L).tenant(tenant).contract(contract)
                .amount(new BigDecimal("5000.00"))
                .status(com.carrental.entity.InvoiceStatus.PARTIALLY_PAID)
                .build();
        when(invoiceRepository.save(any(com.carrental.entity.Invoice.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.updateInvoiceStatus(invoice);

        assertThat(invoice.getStatus()).isEqualTo(com.carrental.entity.InvoiceStatus.PAID);
    }

    @Test
    void updateInvoiceStatus_neverOverwritesCancelledOrRefundedTerminalStates() {
        com.carrental.entity.Tenant tenant = com.carrental.entity.Tenant.builder().id(TENANT_ID).build();
        com.carrental.entity.Invoice cancelled = com.carrental.entity.Invoice.builder()
                .id(402L).tenant(tenant).amount(new BigDecimal("1000.00"))
                .status(com.carrental.entity.InvoiceStatus.CANCELLED).build();

        paymentService.updateInvoiceStatus(cancelled);

        assertThat(cancelled.getStatus()).isEqualTo(com.carrental.entity.InvoiceStatus.CANCELLED);
        verify(invoiceRepository, never()).save(any());
    }
}
