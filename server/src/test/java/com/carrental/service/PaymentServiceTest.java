package com.carrental.service;

import com.carrental.dto.payment.PaymentResponse;
import com.carrental.entity.Payment;
import com.carrental.entity.PaymentMethod;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.PaymentType;
import com.carrental.entity.Reservation;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.ReservationRepository;
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
}
