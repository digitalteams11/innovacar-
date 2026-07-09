package com.carrental.repository;

import com.carrental.entity.AgencyBalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgencyBalanceTransactionRepository extends JpaRepository<AgencyBalanceTransaction, Long> {
    List<AgencyBalanceTransaction> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
