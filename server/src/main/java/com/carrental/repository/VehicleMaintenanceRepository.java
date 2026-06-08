package com.carrental.repository;

import com.carrental.entity.MaintenanceStatus;
import com.carrental.entity.VehicleMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VehicleMaintenanceRepository extends JpaRepository<VehicleMaintenance, Long> {
    List<VehicleMaintenance> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<VehicleMaintenance> findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(Long tenantId, Long vehicleId);
    Optional<VehicleMaintenance> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndVehicleIdAndStatusIn(Long tenantId, Long vehicleId, Collection<MaintenanceStatus> statuses);
}
