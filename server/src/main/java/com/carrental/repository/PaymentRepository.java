package com.carrental.repository;

import com.carrental.entity.Payment;
import com.carrental.entity.PaymentStatus;
import com.carrental.entity.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Tenant-scoped lookup by ID. */
    Optional<Payment> findByIdAndTenantId(Long id, Long tenantId);

    /** All payments for a tenant. */
    List<Payment> findAllByTenantId(Long tenantId);

    /** Count of all payments for a tenant — used by the Super Admin data-reset preview. */
    long countByTenantId(Long tenantId);

    /** Deletes every payment for a tenant — used by the Super Admin data-reset execute. */
    void deleteAllByTenantId(Long tenantId);

    /** Deletes every payment linked to a contract — used by contract trash purge. */
    void deleteAllByContractId(Long contractId);

    /**
     * Native bulk DELETE that bypasses @SQLRestriction on Contract.
     * Spring Data's derived deleteAllByContractId navigates p.contract.id via JPQL,
     * which causes Hibernate to JOIN contracts and apply the soft-delete filter
     * (coalesce(deleted,false)=false) — returning 0 rows for trashed contracts and
     * leaving payment rows behind to cause FK violations on the final contract DELETE.
     * This native query sidesteps all of that.
     */
    @Modifying
    @Query(value = "DELETE FROM payments WHERE contract_id = :contractId", nativeQuery = true)
    int deleteNativeByContractId(@Param("contractId") Long contractId);

    /** All payments for a tenant ordered by date desc. */
    List<Payment> findAllByTenantIdOrderByPaymentDateDesc(Long tenantId);

    /** All payments for a tenant linked to a specific invoice, ordered by date desc. */
    List<Payment> findAllByTenantIdAndInvoiceIdOrderByPaymentDateDesc(Long tenantId, Long invoiceId);

    /** Payments by status within a tenant. */
    List<Payment> findAllByTenantIdAndStatus(Long tenantId, PaymentStatus status);

    /** Payments by type within a tenant. */
    List<Payment> findAllByTenantIdAndType(Long tenantId, PaymentType type);

    /** Payments for a specific client. */
    List<Payment> findAllByTenantIdAndClientIdOrderByPaymentDateDesc(Long tenantId, Long clientId);

    /** Payments for a specific contract. */
    List<Payment> findAllByTenantIdAndContractIdOrderByPaymentDateDesc(Long tenantId, Long contractId);

    /** Payments for a specific reservation. */
    List<Payment> findAllByTenantIdAndReservationIdOrderByPaymentDateDesc(Long tenantId, Long reservationId);

    /** Single payment lookup by reservation (used by service helpers and tests). */
    Optional<Payment> findByReservationIdAndTenantId(Long reservationId, Long tenantId);

    /** Sum of paid amounts for a tenant (rental revenue). */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = :status AND p.type = :type")
    BigDecimal sumAmountByTenantIdAndStatusAndType(@Param("tenantId") Long tenantId,
                                                     @Param("status") PaymentStatus status,
                                                     @Param("type") PaymentType type);

    /**
     * Sum of collected rental revenue, including successful partial payments,
     * net of any partial refund (see Payment#refundedAmount — a partial refund
     * keeps status PAID/PARTIALLY_PAID rather than flipping to REFUNDED, so it
     * must still be excluded from "collected" here rather than only filtering
     * by status).
     */
    @Query("SELECT SUM(p.amount - p.refundedAmount) FROM Payment p WHERE p.tenant.id = :tenantId " +
           "AND p.type = 'RENTAL' AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumCollectedRentalRevenueByTenantId(@Param("tenantId") Long tenantId);

    /** Sum of collected rental revenue in a date range, net of partial refunds — see above. */
    @Query("SELECT SUM(p.amount - p.refundedAmount) FROM Payment p WHERE p.tenant.id = :tenantId " +
           "AND p.type = 'RENTAL' AND p.status IN ('PAID', 'PARTIALLY_PAID') " +
           "AND p.paymentDate >= :start AND p.paymentDate < :end")
    BigDecimal sumCollectedRentalRevenueBetween(@Param("tenantId") Long tenantId,
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    /** Sum of collected payments linked to a contract, net of partial refunds — see above. */
    @Query("SELECT SUM(p.amount - p.refundedAmount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.contract.id = :contractId " +
           "AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumCollectedAmountByTenantIdAndContractId(@Param("tenantId") Long tenantId,
                                                         @Param("contractId") Long contractId);

    /** Sum of collected payments linked to a reservation, net of partial refunds — see above. */
    @Query("SELECT SUM(p.amount - p.refundedAmount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.reservation.id = :reservationId " +
           "AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumCollectedAmountByTenantIdAndReservationId(@Param("tenantId") Long tenantId,
                                                            @Param("reservationId") Long reservationId);

    /** Sum of collected payments linked to an invoice, net of partial refunds — see above. */
    @Query("SELECT SUM(p.amount - p.refundedAmount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.invoice.id = :invoiceId " +
           "AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumCollectedAmountByTenantIdAndInvoiceId(@Param("tenantId") Long tenantId,
                                                        @Param("invoiceId") Long invoiceId);

    /** Tenant-scoped lookup by idempotency key — see Payment#idempotencyKey. */
    Optional<Payment> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    /** Sum of paid amounts for a tenant regardless of type. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = :status")
    BigDecimal sumAmountByTenantIdAndStatus(@Param("tenantId") Long tenantId,
                                            @Param("status") PaymentStatus status);

    /** Monthly revenue for charting — sum of PAID rental payments grouped by month, net of partial refunds. */
    @Query("SELECT FUNCTION('DATE_TRUNC', 'month', p.paymentDate) as month, SUM(p.amount - p.refundedAmount) " +
           "FROM Payment p WHERE p.tenant.id = :tenantId AND p.status IN ('PAID', 'PARTIALLY_PAID') AND p.type = 'RENTAL' " +
           "GROUP BY month ORDER BY month")
    List<Object[]> monthlyRevenueByTenantId(@Param("tenantId") Long tenantId);

    /** Count of payments by status and type. */
    long countByTenantIdAndStatusAndType(Long tenantId, PaymentStatus status, PaymentType type);

    /** Recent transactions limit. */
    List<Payment> findTop5ByTenantIdOrderByPaymentDateDesc(Long tenantId);

    /**
     * Sum of all refunds for a tenant — includes both full refunds (status REFUNDED)
     * and partial refunds still sitting on an otherwise PAID/PARTIALLY_PAID payment
     * (see Payment#refundedAmount), so this is {@code SUM(refundedAmount)} across
     * every payment rather than {@code SUM(amount)} of only fully-refunded rows.
     */
    @Query("SELECT SUM(p.refundedAmount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.refundedAmount > 0")
    BigDecimal sumRefundsByTenantId(@Param("tenantId") Long tenantId);

    /** Count of payments carrying any refund (full or partial) for a tenant. */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.tenant.id = :tenantId AND p.refundedAmount > 0")
    long countRefundsByTenantId(@Param("tenantId") Long tenantId);

    /** Sum of pending amounts for a tenant. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = 'PENDING'")
    BigDecimal sumPendingAmountByTenantId(@Param("tenantId") Long tenantId);

    /** Count of pending payments for a tenant. */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = 'PENDING'")
    long countPendingByTenantId(@Param("tenantId") Long tenantId);

    /** Sum of all PAID payments for a tenant (used by dashboard), net of partial refunds. */
    @Query("SELECT SUM(p.amount - p.refundedAmount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumPaidAmountByTenantId(@Param("tenantId") Long tenantId);

    /** Sum of subscription revenue for super-admin. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'PAID' AND p.type = 'SUBSCRIPTION'")
    BigDecimal sumSubscriptionRevenue();

    /** Monthly subscription revenue. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'PAID' AND p.type = 'SUBSCRIPTION' " +
           "AND p.paymentDate >= :start AND p.paymentDate < :end")
    BigDecimal sumSubscriptionRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Count failed subscription payments. */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status IN ('FAILED', 'EXPIRED') AND p.type = 'SUBSCRIPTION'")
    long countFailedSubscriptionPayments();

    /**
     * Every non-subscription payment recorded within a closed reporting period,
     * regardless of status/type — the reporting calculation engine classifies
     * revenue/expense/refund recognition itself so it stays testable in Java
     * rather than baked into ad-hoc SQL per metric.
     */
    @Query("SELECT p FROM Payment p WHERE p.tenant.id = :tenantId AND p.type <> 'SUBSCRIPTION' " +
           "AND p.paymentDate >= :start AND p.paymentDate < :end")
    List<Payment> findAllForReportingPeriod(@Param("tenantId") Long tenantId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);
}
