package com.carrental.repository;

import com.carrental.entity.CustomRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomRolePermissionRepository extends JpaRepository<CustomRolePermission, Long> {
    List<CustomRolePermission> findAllByCustomRoleId(Long customRoleId);

    Optional<CustomRolePermission> findByCustomRoleIdAndPermissionCode(Long customRoleId, String permissionCode);

    void deleteAllByCustomRoleId(Long customRoleId);
}
