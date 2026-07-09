package com.carrental.repository;

import com.carrental.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    List<EmailLog> findTop100ByOrderByCreatedAtDesc();

    long countByStatus(String status);

    boolean existsByContractIdAndEmailTypeAndStatus(Long contractId, String emailType, String status);

    List<EmailLog> findAllByContractIdOrderByCreatedAtDesc(Long contractId);

    List<EmailLog> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<EmailLog> findTop20ByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
