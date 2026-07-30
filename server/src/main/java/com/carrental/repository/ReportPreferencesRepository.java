package com.carrental.repository;

import com.carrental.entity.ReportPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportPreferencesRepository extends JpaRepository<ReportPreferences, Long> {
    Optional<ReportPreferences> findByTenantId(Long tenantId);
}
