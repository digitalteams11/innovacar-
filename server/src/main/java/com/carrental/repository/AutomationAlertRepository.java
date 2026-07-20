package com.carrental.repository;

import com.carrental.entity.AutomationAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationAlertRepository extends JpaRepository<AutomationAlert, Long> {
    List<AutomationAlert> findAllByTenantIdOrTenantIdIsNullOrderByCreatedAtDesc(Long tenantId);
    long countByTenantIdAndAcknowledgedFalse(Long tenantId);
    boolean existsByTenantIdAndAgentKeyAndAcknowledgedFalse(Long tenantId, String agentKey);
}
