package com.carrental.repository;

import com.carrental.entity.AutomationAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutomationAgentRepository extends JpaRepository<AutomationAgent, Long> {
    Optional<AutomationAgent> findByTenantIdAndAgentKey(Long tenantId, String agentKey);
    Optional<AutomationAgent> findByTenantIdIsNullAndAgentKey(String agentKey);
    List<AutomationAgent> findAllByTenantId(Long tenantId);
}
