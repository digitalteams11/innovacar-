package com.carrental.repository;

import com.carrental.entity.Deposit;
import com.carrental.entity.DepositStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface DepositRepository extends JpaRepository<Deposit, Long> {

    List<Deposit> findAllByTenantId(Long tenantId);

    List<Deposit> findAllByClientIdAndTenantId(Long clientId, Long tenantId);

    Optional<Deposit> findByContractId(Long contractId);

    Optional<Deposit> findByReservationId(Long reservationId);

    Optional<Deposit> findByIdAndTenantId(Long id, Long tenantId);

    // ── Aggregations for dashboard ───────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Deposit d WHERE d.tenant.id = :tenantId AND d.status IN ('RECEIVED', 'HELD')")
    BigDecimal sumActiveDepositsByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(d) FROM Deposit d WHERE d.tenant.id = :tenantId AND d.status = 'HELD'")
    Long countPendingReturnsByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(d.returnedAmount), 0) FROM Deposit d WHERE d.tenant.id = :tenantId AND d.status IN ('RETURNED', 'PARTIALLY_RETURNED')")
    BigDecimal sumReturnedDepositsByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(d.damageDeduction + d.cleaningDeduction + d.lateFeeDeduction + d.fuelDeduction + d.otherDeduction), 0) FROM Deposit d WHERE d.tenant.id = :tenantId")
    BigDecimal sumTotalDeductionsByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(d.damageDeduction + d.cleaningDeduction + d.lateFeeDeduction + d.fuelDeduction + d.otherDeduction), 0) FROM Deposit d WHERE d.tenant.id = :tenantId AND d.status IN ('DEDUCTED', 'PARTIALLY_RETURNED')")
    BigDecimal sumDepositRevenueByTenantId(@Param("tenantId") Long tenantId);

    long countByStatusAndTenantId(DepositStatus status, Long tenantId);

    // ── Client summary ───────────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Deposit d WHERE d.client.id = :clientId AND d.tenant.id = :tenantId")
    BigDecimal sumTotalDepositsByClientId(@Param("clientId") Long clientId, @Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Deposit d WHERE d.client.id = :clientId AND d.tenant.id = :tenantId AND d.status IN ('RECEIVED', 'HELD')")
    BigDecimal sumActiveDepositsByClientId(@Param("clientId") Long clientId, @Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(d.returnedAmount), 0) FROM Deposit d WHERE d.client.id = :clientId AND d.tenant.id = :tenantId AND d.status IN ('RETURNED', 'PARTIALLY_RETURNED')")
    BigDecimal sumReturnedDepositsByClientId(@Param("clientId") Long clientId, @Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(d) FROM Deposit d WHERE d.client.id = :clientId AND d.tenant.id = :tenantId AND d.status = 'PENDING'")
    Long countPendingDepositsByClientId(@Param("clientId") Long clientId, @Param("tenantId") Long tenantId);
}
