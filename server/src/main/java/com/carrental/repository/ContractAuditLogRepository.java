package com.carrental.repository;

import com.carrental.entity.ContractAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractAuditLogRepository extends JpaRepository<ContractAuditLog, Long> {

    List<ContractAuditLog> findAllByContractIdOrderByCreatedAtDesc(Long contractId);
}
