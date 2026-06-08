package com.carrental.repository;

import com.carrental.entity.AffiliateReferral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AffiliateReferralRepository extends JpaRepository<AffiliateReferral, Long> {
    Optional<AffiliateReferral> findByReferralCode(String referralCode);
    Optional<AffiliateReferral> findByReferrerTenantId(Long tenantId);
}
