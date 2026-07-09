package com.carrental.repository;

import com.carrental.entity.PromoCodeRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, Long> {
    List<PromoCodeRedemption> findAllByPromoCodeIdOrderByRedeemedAtDesc(Long promoCodeId);
    long countByPromoCodeIdAndTenantId(Long promoCodeId, Long tenantId);
    long countByPromoCodeIdAndTenantIdAndStatus(Long promoCodeId, Long tenantId, String status);
    long countByPromoCodeIdAndStatus(Long promoCodeId, String status);
}
