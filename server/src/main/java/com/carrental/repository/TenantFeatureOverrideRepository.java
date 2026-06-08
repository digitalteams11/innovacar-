package com.carrental.repository;

import com.carrental.entity.TenantFeatureOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TenantFeatureOverrideRepository extends JpaRepository<TenantFeatureOverride, Long> {
    List<TenantFeatureOverride> findAllByTenantIdAndEnabledTrueAndStartsAtLessThanEqualAndExpiresAtAfter(
            Long tenantId, LocalDateTime startsAt, LocalDateTime expiresAt);

    boolean existsByTenantIdAndFeatureCodeAndEnabledTrueAndStartsAtLessThanEqualAndExpiresAtAfter(
            Long tenantId, String featureCode, LocalDateTime startsAt, LocalDateTime expiresAt);
}
