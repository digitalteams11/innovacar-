package com.carrental.repository;

import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /** All vehicles belonging to a tenant. */
    List<Vehicle> findAllByTenantId(Long tenantId);

    /** All vehicles of a specific status within a tenant — used for filtering. */
    List<Vehicle> findAllByTenantIdAndStatut(Long tenantId, VehicleStatus statut);

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Vehicle> findByIdAndTenantId(Long id, Long tenantId);

    /** Existence check to guard delete / update. */
    boolean existsByIdAndTenantId(Long id, Long tenantId);

    /** Count available vehicles per tenant — useful for dashboard. */
    long countByTenantIdAndStatut(Long tenantId, VehicleStatus statut);

    /** Count total vehicles per tenant. */
    long countByTenantId(Long tenantId);
}
