package com.carrental.repository;

import com.carrental.entity.OnboardingProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingProgressRepository extends JpaRepository<OnboardingProgress, Long> {
    Optional<OnboardingProgress> findByTenantId(Long tenantId);
}
