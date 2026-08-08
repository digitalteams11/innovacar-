package com.carrental.repository;

import com.carrental.entity.ContractExtension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContractExtensionRepository extends JpaRepository<ContractExtension, Long> {

    /** All extensions recorded against a contract, oldest first — the extension timeline. */
    List<ContractExtension> findAllByTenantIdAndContractIdOrderByCreatedAtAsc(Long tenantId, Long contractId);

    /** Extensions recorded within a date range — used by the monthly accounting summary
     *  to break out "revenue from extensions" separately from base rental revenue. */
    List<ContractExtension> findAllByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime start, LocalDateTime end);
}
