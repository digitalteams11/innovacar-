package com.carrental.repository;

import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    long countByBranchId(Long branchId);

    /** All vehicles belonging to a tenant. */
    List<Vehicle> findAllByTenantId(Long tenantId);

    /** All vehicles of a specific status within a tenant — used for filtering. */
    List<Vehicle> findAllByTenantIdAndStatut(Long tenantId, VehicleStatus statut);

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Vehicle> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id and v.tenant.id = :tenantId")
    Optional<Vehicle> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** Existence check to guard delete / update. */
    boolean existsByIdAndTenantId(Long id, Long tenantId);

    /** Count available vehicles per tenant — useful for dashboard. */
    long countByTenantIdAndStatut(Long tenantId, VehicleStatus statut);

    /**
     * Count available vehicles per tenant, treating a never-set (null) status as
     * AVAILABLE — matches the default applied in {@code VehicleResponse}, so vehicles
     * inserted without an explicit status are not silently excluded from the count.
     */
    @Query("select count(v) from Vehicle v where v.tenant.id = :tenantId " +
            "and (v.statut = com.carrental.entity.VehicleStatus.AVAILABLE or v.statut is null)")
    long countAvailableByTenantId(@Param("tenantId") Long tenantId);

    /** Count total vehicles per tenant. */
    long countByTenantId(Long tenantId);

    /** GPS-enabled vehicles for a tenant. */
    List<Vehicle> findAllByTenantIdAndGpsEnabledTrue(Long tenantId);

    /** Find vehicle by GPS device ID within a tenant. */
    Optional<Vehicle> findByGpsDeviceIdAndTenantId(String gpsDeviceId, Long tenantId);

    /** Count GPS-enabled vehicles (= GPS devices linked to this tenant). */
    long countByTenantIdAndGpsEnabledTrue(Long tenantId);

    /** Count GPS-enabled vehicles currently flagged as out of zone. */
    long countByTenantIdAndGpsEnabledTrueAndOutOfZoneTrue(Long tenantId);

    /**
     * Bypasses {@code @SQLRestriction} to fetch a single trashed vehicle for
     * restore/purge — normal finders silently exclude {@code deleted=true} rows.
     */
    @Query(value = "SELECT * FROM vehicles WHERE id = :id AND tenant_id = :tenantId AND deleted = true",
            nativeQuery = true)
    Optional<Vehicle> findDeletedByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** Every trashed vehicle for a tenant, most recently deleted first — for the Trash page. */
    @Query(value = "SELECT * FROM vehicles WHERE tenant_id = :tenantId AND deleted = true ORDER BY deleted_at DESC",
            nativeQuery = true)
    List<Vehicle> findAllTrashedByTenantId(@Param("tenantId") Long tenantId);

    /** Trashed vehicles whose retention window has expired — used by the daily auto-purge job. */
    @Query(value = "SELECT * FROM vehicles WHERE deleted = true AND deleted_at < :cutoff", nativeQuery = true)
    List<Vehicle> findExpiredTrash(@Param("cutoff") LocalDateTime cutoff);
}
