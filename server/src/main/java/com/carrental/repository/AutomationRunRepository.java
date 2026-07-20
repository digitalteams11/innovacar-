package com.carrental.repository;

import com.carrental.entity.AutomationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {
    List<AutomationRun> findAllByTenantIdOrderByStartedAtDesc(Long tenantId);
    List<AutomationRun> findAllByTenantIdIsNullOrderByStartedAtDesc();
    List<AutomationRun> findAllByTenantIdAndStartedAtAfter(Long tenantId, LocalDateTime after);
    List<AutomationRun> findAllByTenantIdIsNullAndStartedAtAfter(LocalDateTime after);
}
