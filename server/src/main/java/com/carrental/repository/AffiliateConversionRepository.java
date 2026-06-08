package com.carrental.repository;

import com.carrental.entity.AffiliateConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AffiliateConversionRepository extends JpaRepository<AffiliateConversion, Long> {
    Optional<AffiliateConversion> findByReferredTenantId(Long tenantId);
}
