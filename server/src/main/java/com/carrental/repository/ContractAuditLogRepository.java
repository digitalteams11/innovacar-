package com.carrental.repository;

import com.carrental.entity.ContractAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractAuditLogRepository extends JpaRepository<ContractAuditLog, Long> {

    List<ContractAuditLog> findAllByContractIdOrderByCreatedAtDesc(Long contractId);

    void deleteAllByContractIdIn(List<Long> contractIds);

    /** Native bulk DELETE — bypasses @SQLRestriction on Contract (see PaymentRepository for details). */
    @Modifying
    @Query(value = "DELETE FROM contract_audit_logs WHERE contract_id = :contractId", nativeQuery = true)
    int deleteNativeByContractId(@Param("contractId") Long contractId);
}
