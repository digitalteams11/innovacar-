package com.carrental.repository;

import com.carrental.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByTenantIdOrderByNameAsc(Long tenantId);
    Optional<Branch> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByTenantIdAndCodeIgnoreCase(Long tenantId, String code);
    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);
    long countByTenantId(Long tenantId);
    Optional<Branch> findByTenantIdAndIsDefaultTrue(Long tenantId);
}
