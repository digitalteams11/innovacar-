package com.carrental.repository;

import com.carrental.entity.SuperAdminRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuperAdminRolePermissionRepository extends JpaRepository<SuperAdminRolePermission, Long> {
    List<SuperAdminRolePermission> findAllByRoleId(Long roleId);
    List<SuperAdminRolePermission> findAllByRoleIdAndPermissionCode(Long roleId, String permissionCode);
}
