package com.carrental.repository;

import com.carrental.entity.Role;
import com.carrental.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findAllByTenantIdAndRole(Long tenantId, Role role);
    Optional<RolePermission> findByTenantIdAndRoleAndPermissionCode(Long tenantId, Role role, String permissionCode);
    boolean existsByTenantIdAndRoleAndPermissionCodeAndEnabledTrue(
            Long tenantId, Role role, String permissionCode);
}
