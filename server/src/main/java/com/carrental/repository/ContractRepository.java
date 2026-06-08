package com.carrental.repository;

import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    /** All contracts belonging to a tenant. */
    List<Contract> findAllByTenantId(Long tenantId);

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Contract> findByIdAndTenantId(Long id, Long tenantId);

    /** All contracts of a specific status within a tenant — used for filtering. */
    List<Contract> findAllByTenantIdAndStatus(Long tenantId, ContractStatus status);

    /** Find contract by its unique QR token (public signing link). */
    Optional<Contract> findByQrToken(String qrToken);

    /** Find contract by contract number. */
    Optional<Contract> findByContractNumber(String contractNumber);

    /** All contracts for a specific client within a tenant. */
    List<Contract> findAllByTenantIdAndClientId(Long tenantId, Long clientId);
}
