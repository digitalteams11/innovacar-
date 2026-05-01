package com.carrental.service;

import com.carrental.dto.payment.PaymentResponse;
import com.carrental.entity.Payment;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.PaymentRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment-management business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Gets the payment associated with a reservation.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByReservation(Long reservationId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Payment payment = paymentRepository.findByReservationIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for reservation: " + reservationId));
        return PaymentResponse.from(payment);
    }

    /**
     * Marks a reservation's payment as paid.
     */
    @Transactional
    public PaymentResponse markAsPaid(Long reservationId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Payment payment = paymentRepository.findByReservationIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for reservation: " + reservationId));

        if (payment.isPaid()) {
            throw new IllegalStateException("Payment for reservation " + reservationId + " is already marked as paid.");
        }

        payment.setPaid(true);
        Payment saved = paymentRepository.save(payment);
        
        log.info("Payment [id={}] for reservation [id={}] marked as paid in tenant [{}]",
                saved.getId(), reservationId, tenantId);
                
        return PaymentResponse.from(saved);
    }
}
