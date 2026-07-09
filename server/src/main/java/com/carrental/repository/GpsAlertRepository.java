package com.carrental.repository;

import com.carrental.entity.GpsAlert;
import com.carrental.entity.GpsAlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GpsAlertRepository extends JpaRepository<GpsAlert, Long> {

    List<GpsAlert> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<GpsAlert> findAllByTenantIdAndReadFalseOrderByCreatedAtDesc(Long tenantId);

    long countByTenantIdAndReadFalse(Long tenantId);

    List<GpsAlert> findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(Long tenantId, Long vehicleId);

    long countByTenantId(Long tenantId);

    /** Count alerts created since {@code since} (used for "alerts today" KPI). */
    long countByTenantIdAndCreatedAtAfter(Long tenantId, LocalDateTime since);

    /**
     * Deduplication guard: returns true if an alert of the same type already
     * exists for this vehicle within the given window, preventing spam on
     * every refresh cycle.
     */
    boolean existsByTenantIdAndAlertTypeAndVehicleIdAndCreatedAtAfter(
            Long tenantId, GpsAlertType alertType, Long vehicleId, LocalDateTime since);

    void deleteAllByTenantId(Long tenantId);
}
