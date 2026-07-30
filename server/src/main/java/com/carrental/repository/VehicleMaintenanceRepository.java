package com.carrental.repository;

import com.carrental.entity.MaintenanceStatus;
import com.carrental.entity.VehicleMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VehicleMaintenanceRepository extends JpaRepository<VehicleMaintenance, Long> {
    List<VehicleMaintenance> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<VehicleMaintenance> findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(Long tenantId, Long vehicleId);
    Optional<VehicleMaintenance> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndVehicleIdAndStatusIn(Long tenantId, Long vehicleId, Collection<MaintenanceStatus> statuses);
    Optional<VehicleMaintenance> findFirstByTenantIdAndVehicleIdAndStatusOrderByCreatedAtDesc(
            Long tenantId, Long vehicleId, MaintenanceStatus status);

    /** Batch lookup for the fleet export's "next maintenance date" column — avoids one query per vehicle. */
    List<VehicleMaintenance> findAllByTenantIdAndVehicleIdInAndStatus(
            Long tenantId, Collection<Long> vehicleIds, MaintenanceStatus status);

    @Query(value = "SELECT vehicle_id FROM vehicle_maintenance WHERE id = :id", nativeQuery = true)
    Long findVehicleIdById(@Param("id") Long id);

    /** Maintenance orders created within a reporting period — order counts/upcoming-scheduled metrics. */
    List<VehicleMaintenance> findAllByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long tenantId, LocalDateTime start, LocalDateTime end);

    /** Maintenance completed within a reporting period — this is when the cost is recognized as an expense. */
    List<VehicleMaintenance> findAllByTenantIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long tenantId, MaintenanceStatus status, LocalDateTime start, LocalDateTime end);
}
