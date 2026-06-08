package com.carrental.repository;

import com.carrental.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {
    List<PlanFeature> findAllByPlanId(Long planId);
    List<PlanFeature> findAllByPlanCode(String planCode);
    List<PlanFeature> findAllByFeatureCodeAndEnabledTrue(String featureCode);
    Optional<PlanFeature> findByPlanIdAndFeatureCode(Long planId, String featureCode);
    boolean existsByPlanIdAndFeatureCodeAndEnabledTrue(Long planId, String featureCode);
    void deleteAllByFeatureCode(String featureCode);
}
