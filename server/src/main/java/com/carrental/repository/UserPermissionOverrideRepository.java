package com.carrental.repository;

import com.carrental.entity.UserPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, Long> {
    List<UserPermissionOverride> findAllByUserId(Long userId);

    Optional<UserPermissionOverride> findByUserIdAndPermissionCode(Long userId, String permissionCode);

    void deleteByUserIdAndPermissionCode(Long userId, String permissionCode);
}
