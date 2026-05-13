package com.carrental.repository;

import com.carrental.entity.GpsSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GpsSettingsRepository extends JpaRepository<GpsSettings, Long> {

    Optional<GpsSettings> findByTenantId(Long tenantId);

    boolean existsByTenantId(Long tenantId);
}
