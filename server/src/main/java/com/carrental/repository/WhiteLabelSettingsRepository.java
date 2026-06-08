package com.carrental.repository;

import com.carrental.entity.WhiteLabelSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhiteLabelSettingsRepository extends JpaRepository<WhiteLabelSettings, Long> {
    Optional<WhiteLabelSettings> findByTenantId(Long tenantId);
    Optional<WhiteLabelSettings> findByCustomDomain(String customDomain);
}
