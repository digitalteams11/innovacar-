package com.carrental.repository;

import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    /** All contracts belonging to a tenant. */
    List<Contract> findAllByTenantId(Long tenantId);

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Contract> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Raw existence/ownership probe that bypasses the {@code @SQLRestriction}
     * soft-delete filter on {@link Contract} — used only to log a precise
     * diagnosis (missing vs. wrong tenant vs. already deleted) when a delete
     * request 404s, since every normal JPA/HQL fetch on this entity silently
     * excludes deleted rows.
     */
    @Query(value = "SELECT tenant_id, deleted, status FROM contracts WHERE id = :id", nativeQuery = true)
    Optional<Object[]> findRawStateById(@Param("id") Long id);

    /** All contracts of a specific status within a tenant — used for filtering. */
    List<Contract> findAllByTenantIdAndStatus(Long tenantId, ContractStatus status);

    /** Count contracts whose status is one of the given set within a tenant — used for dashboard. */
    long countByTenantIdAndStatusIn(Long tenantId, List<ContractStatus> statuses);

    /** Find contract by its unique QR token (public signing link). */
    Optional<Contract> findByQrToken(String qrToken);

    /** Find contract by contract number. */
    Optional<Contract> findByContractNumber(String contractNumber);

    /**
     * Raw uniqueness probe that bypasses the {@code @SQLRestriction} soft-delete
     * filter — {@code contract_number} carries a global unique DB index that
     * still includes soft-deleted rows, so the generator must check against
     * the real table contents, not the filtered {@code findByContractNumber}.
     * Without this, a freshly deleted contract's number looks "free" to
     * {@link #findByContractNumber}, the generator reuses it, and the INSERT
     * fails with a DB-level unique violation (surfaced as a 409).
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM contracts WHERE contract_number = :contractNumber)",
            nativeQuery = true)
    boolean existsByContractNumberIncludingDeleted(@Param("contractNumber") String contractNumber);

    /**
     * Raw FK probe that bypasses {@code @SQLRestriction} — checks whether any contract
     * (including soft-deleted ones) holds this {@code reservation_id}. Used in
     * {@code directCreateContract} to detect orphaned AUTO_FROM_CONTRACT reservations
     * whose backing contract was trashed: @SQLRestriction hides the deleted contract
     * from {@code reservation.getContract()}, but the DB unique constraint on
     * {@code contracts.reservation_id} still blocks a new contract from reusing the same
     * reservation FK — causing a spurious DATA_CONFLICT 409.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM contracts WHERE reservation_id = :reservationId)",
            nativeQuery = true)
    boolean existsByReservationIdIncludingDeleted(@Param("reservationId") Long reservationId);

    /** All contracts for a specific client within a tenant. */
    List<Contract> findAllByTenantIdAndClientId(Long tenantId, Long clientId);

    /** Contracts missing a linked reservation — used to backfill legacy/orphaned rows. */
    List<Contract> findAllByTenantIdAndReservationIsNull(Long tenantId);

    /**
     * Idempotency fallback for direct-create: matches an existing contract by
     * tenant+client+vehicle+exact dates regardless of which reservation (if any)
     * it's linked to, so a retried/duplicate submission returns the existing
     * contract instead of a false 409 when the reservation-based lookup misses it.
     */
    java.util.List<Contract> findAllByTenantIdAndClientIdAndVehicleIdAndStartDateAndEndDateAndStatusNot(
            Long tenantId, Long clientId, Long vehicleId,
            java.time.LocalDate startDate, java.time.LocalDate endDate, ContractStatus status);

    @Query("SELECT COUNT(c) > 0 FROM Contract c WHERE c.vehicle.id = :vehicleId " +
           "AND c.tenant.id = :tenantId AND c.status IN :statuses")
    boolean existsActiveVehicleContract(
            @Param("vehicleId") Long vehicleId,
            @Param("tenantId") Long tenantId,
            @Param("statuses") List<ContractStatus> statuses);


    /** Debug/admin lookup for every contract attached to a vehicle. */
    List<Contract> findAllByVehicleId(Long vehicleId);

    /** Debug/admin lookup for contracts attached to a vehicle with a specific status. */
    List<Contract> findAllByVehicleIdAndStatus(Long vehicleId, ContractStatus status);

    /**
     * Native conflict probe avoids JPQL enum/property translation surprises in
     * availability checks. The explicit {@code deleted} check is required
     * because this is a native query and therefore bypasses the entity's
     * {@code @SQLRestriction} soft-delete filter — trashed contracts must
     * never block availability, even if their status hasn't been forced to
     * CANCELLED.
     */
    @Query(value = """
            SELECT * FROM contracts
            WHERE tenant_id = :tenantId
              AND vehicle_id = :vehicleId
              AND (:excludeContractId IS NULL OR id != :excludeContractId)
              AND contract_status NOT IN ('CANCELLED', 'COMPLETED', 'EXPIRED', 'DRAFT')
              AND coalesce(deleted, false) = false
              AND start_date < :endDate
              AND end_date > :startDate
            """, nativeQuery = true)
    List<Contract> findConflictingContracts(
            @Param("tenantId") Long tenantId,
            @Param("vehicleId") Long vehicleId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeContractId") Long excludeContractId);
    long countByTenantId(Long tenantId);

    /** Deletes every contract for a tenant — used by the Super Admin data-reset execute. */
    void deleteAllByTenantId(Long tenantId);

    /**
     * Native DELETE that bypasses the {@code @SQLRestriction} filter on {@link Contract}.
     * Used by {@link com.carrental.service.ContractPurgeService} to hard-delete a trashed
     * (deleted=true) contract row — {@code em.find()} with the filter active would return
     * null and silently skip the JPA {@code delete(entity)} call, leaving the row in the DB.
     */
    @Modifying
    @Query(value = "DELETE FROM contracts WHERE id = :id", nativeQuery = true)
    void deleteNativeById(@Param("id") Long id);

    /**
     * Bypasses {@code @SQLRestriction} to fetch a single trashed contract for
     * restore/purge — normal finders silently exclude {@code deleted=true} rows.
     */
    @Query(value = "SELECT * FROM contracts WHERE id = :id AND tenant_id = :tenantId AND deleted = true",
            nativeQuery = true)
    Optional<Contract> findDeletedByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** Every trashed contract for a tenant, most recently deleted first — for the Trash page. */
    @Query(value = "SELECT * FROM contracts WHERE tenant_id = :tenantId AND deleted = true ORDER BY deleted_at DESC",
            nativeQuery = true)
    List<Contract> findAllTrashedByTenantId(@Param("tenantId") Long tenantId);

    /** Trashed contracts whose retention window has expired — used by the daily auto-purge job. */
    @Query(value = "SELECT * FROM contracts WHERE deleted = true AND deleted_at < :cutoff", nativeQuery = true)
    List<Contract> findExpiredTrash(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Native existence probe used to block vehicle purge — deliberately bypasses
     * {@code @SQLRestriction} because even a trashed-but-not-yet-purged contract
     * still holds a {@code vehicle_id} FK row that a hard vehicle delete would
     * violate.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM contracts WHERE vehicle_id = :vehicleId)", nativeQuery = true)
    boolean existsAnyContractForVehicleId(@Param("vehicleId") Long vehicleId);
}
