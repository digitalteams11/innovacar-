package com.carrental.repository;

import com.carrental.entity.CustomRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomRoleRepository extends JpaRepository<CustomRole, Long> {
    List<CustomRole> findAllByTenantId(Long tenantId);

    Optional<CustomRole> findByTenantIdAndCode(Long tenantId, String code);

    Optional<CustomRole> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);
}
