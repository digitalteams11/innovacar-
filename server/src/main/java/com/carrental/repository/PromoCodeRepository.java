package com.carrental.repository;

import com.carrental.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    List<PromoCode> findAllByIsActiveTrue();
    List<PromoCode> findAllByDeletedFalseOrderByCreatedAtDesc();
    List<PromoCode> findAllByDeletedFalseAndIsActiveTrueOrderByCreatedAtDesc();
    Optional<PromoCode> findByCode(String code);
    Optional<PromoCode> findByCodeAndDeletedFalse(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    boolean existsByCode(String code);

    @Query(value = "SELECT COUNT(*) FROM promo_code_redemptions WHERE promo_code_id = :promoId AND tenant_id = :tenantId",
           nativeQuery = true)
    long countRedemptionsByPromoAndTenant(@Param("promoId") Long promoId, @Param("tenantId") Long tenantId);
}
