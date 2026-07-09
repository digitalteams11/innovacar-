package com.carrental.repository;

import com.carrental.entity.PromoCodePlanLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoCodePlanLinkRepository extends JpaRepository<PromoCodePlanLink, Long> {
    List<PromoCodePlanLink> findAllByPromoCodeId(Long promoCodeId);
    Optional<PromoCodePlanLink> findByPromoCodeIdAndPlanCodeIgnoreCaseAndBillingCycleIgnoreCaseAndActiveTrue(
            Long promoCodeId, String planCode, String billingCycle);
    void deleteAllByPromoCodeId(Long promoCodeId);
}
