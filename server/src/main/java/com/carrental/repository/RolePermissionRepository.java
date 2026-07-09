package com.carrental.repository;

import com.carrental.entity.Role;
import com.carrental.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findAllByTenantIdAndRole(Long tenantId, Role role);
    // List (not Optional) on purpose: legacy duplicate rows for the same
    // tenant+role+code would make an Optional-returning query throw
    // IncorrectResultSizeDataAccessException and 500 the whole matrix endpoint.
    List<RolePermission> findAllByTenantIdAndRoleAndPermissionCode(Long tenantId, Role role, String permissionCode);
    boolean existsByTenantIdAndRoleAndPermissionCodeAndEnabledTrue(
            Long tenantId, Role role, String permissionCode);
}
