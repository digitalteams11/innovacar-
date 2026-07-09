package com.carrental.repository;

import com.carrental.entity.GpsDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GpsDeviceRepository extends JpaRepository<GpsDevice, Long> {

    List<GpsDevice> findAllByTenantId(Long tenantId);

    Optional<GpsDevice> findByTenantIdAndProviderDeviceId(Long tenantId, String providerDeviceId);

    Optional<GpsDevice> findByIdAndTenantId(Long id, Long tenantId);

    List<GpsDevice> findAllByTenantIdAndVehicleId(Long tenantId, Long vehicleId);

    void deleteAllByTenantId(Long tenantId);
}
