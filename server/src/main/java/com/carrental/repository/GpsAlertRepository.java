package com.carrental.repository;

import com.carrental.entity.GpsAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GpsAlertRepository extends JpaRepository<GpsAlert, Long> {

    List<GpsAlert> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<GpsAlert> findAllByTenantIdAndReadFalseOrderByCreatedAtDesc(Long tenantId);

    long countByTenantIdAndReadFalse(Long tenantId);

    List<GpsAlert> findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(Long tenantId, Long vehicleId);
}
