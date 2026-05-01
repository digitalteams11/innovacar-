package com.carrental.repository;

import com.carrental.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Tenant-scoped lookup by reservation ID. */
    Optional<Payment> findByReservationIdAndTenantId(Long reservationId, Long tenantId);

    /** Tenant-scoped lookup by ID. */
    Optional<Payment> findByIdAndTenantId(Long id, Long tenantId);

    /** Calculate total revenue (sum of paid amounts) for a tenant. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.paid = true")
    java.math.BigDecimal sumPaidAmountByTenantId(@org.springframework.data.repository.query.Param("tenantId") Long tenantId);
}
