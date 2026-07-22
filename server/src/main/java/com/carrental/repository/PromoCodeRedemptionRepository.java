package com.carrental.repository;

import com.carrental.entity.PromoCodeRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, Long> {
    List<PromoCodeRedemption> findAllByPromoCodeIdOrderByRedeemedAtDesc(Long promoCodeId);
    long countByPromoCodeIdAndTenantId(Long promoCodeId, Long tenantId);
    long countByPromoCodeIdAndTenantIdAndStatus(Long promoCodeId, Long tenantId, String status);
    long countByPromoCodeIdAndStatus(Long promoCodeId, String status);

    /**
     * Most recent RESERVED reservation for this tenant+promo — used by the Whop
     * webhook handler to flip a reservation to USED once payment is actually
     * confirmed (never on checkout-link creation alone). If the same tenant
     * opened checkout more than once for the same promo without paying, only
     * the latest reservation is resolved; older abandoned ones are left
     * RESERVED and simply age out of relevance (they don't count toward
     * per-agency/global usage caps, which only count status="USED").
     */
    Optional<PromoCodeRedemption> findFirstByPromoCode_CodeIgnoreCaseAndTenantIdAndStatusOrderByRedeemedAtDesc(
            String promoCode, Long tenantId, String status);
}
