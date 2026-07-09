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

    /** Sum of collected rental revenue, including successful partial payments. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId " +
           "AND p.type = 'RENTAL' AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumCollectedRentalRevenueByTenantId(@Param("tenantId") Long tenantId);

    /** Sum of collected rental revenue in a date range, including successful partial payments. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId " +
           "AND p.type = 'RENTAL' AND p.status IN ('PAID', 'PARTIALLY_PAID') " +
           "AND p.paymentDate >= :start AND p.paymentDate < :end")
    BigDecimal sumCollectedRentalRevenueBetween(@Param("tenantId") Long tenantId,
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    /** Sum of collected payments linked to a contract. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.contract.id = :contractId " +
           "AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumCollectedAmountByTenantIdAndContractId(@Param("tenantId") Long tenantId,
                                                         @Param("contractId") Long contractId);

    /** Sum of collected payments linked to a reservation. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.reservation.id = :reservationId " +
           "AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumCollectedAmountByTenantIdAndReservationId(@Param("tenantId") Long tenantId,
                                                            @Param("reservationId") Long reservationId);

    /** Sum of collected payments linked to an invoice. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.invoice.id = :invoiceId " +
           "AND p.status IN ('PAID', 'PARTIALLY_PAID')")
    BigDecimal sumCollectedAmountByTenantIdAndInvoiceId(@Param("tenantId") Long tenantId,
                                                        @Param("invoiceId") Long invoiceId);

    /** Sum of paid amounts for a tenant regardless of type. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = :status")
    BigDecimal sumAmountByTenantIdAndStatus(@Param("tenantId") Long tenantId,
                                            @Param("status") PaymentStatus status);

    /** Monthly revenue for charting — sum of PAID rental payments grouped by month. */
    @Query("SELECT FUNCTION('DATE_TRUNC', 'month', p.paymentDate) as month, SUM(p.amount) " +
           "FROM Payment p WHERE p.tenant.id = :tenantId AND p.status IN ('PAID', 'PARTIALLY_PAID') AND p.type = 'RENTAL' " +
           "GROUP BY month ORDER BY month")
    List<Object[]> monthlyRevenueByTenantId(@Param("tenantId") Long tenantId);

    /** Count of payments by status and type. */
    long countByTenantIdAndStatusAndType(Long tenantId, PaymentStatus status, PaymentType type);

    /** Recent transactions limit. */
    List<Payment> findTop5ByTenantIdOrderByPaymentDateDesc(Long tenantId);

    /** Sum of refunds for a tenant. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = 'REFUNDED'")
    BigDecimal sumRefundsByTenantId(@Param("tenantId") Long tenantId);

    /** Count of refunds for a tenant. */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = 'REFUNDED'")
    long countRefundsByTenantId(@Param("tenantId") Long tenantId);

    /** Sum of pending amounts for a tenant. */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = 'PENDING'")
    BigDecimal sumPendingAmountByTenantId(@Param("tenantId") Long tenantId);

    /** Count of pending payments for a tenant. */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = 'PENDING'")
    long countPendingByTenantId(@Param("tenantId") Long tenantId);

    /** Sum of all PAID payments for a tenant (used by dashboard). */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status IN ('PAID', 'PARTIALLY_PAID')")
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
}
