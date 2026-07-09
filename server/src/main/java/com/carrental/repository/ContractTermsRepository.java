package com.carrental.repository;

import com.carrental.entity.ContractTerms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractTermsRepository extends JpaRepository<ContractTerms, Long> {
    List<ContractTerms> findAllByTenantIdOrderByDefaultTermsDescUpdatedAtDesc(Long tenantId);
    Optional<ContractTerms> findByIdAndTenantId(Long id, Long tenantId);
    Optional<ContractTerms> findFirstByTenantIdAndDefaultTermsTrueOrderByUpdatedAtDesc(Long tenantId);
    List<ContractTerms> findAllByTenantIdAndDefaultTermsTrue(Long tenantId);
}
