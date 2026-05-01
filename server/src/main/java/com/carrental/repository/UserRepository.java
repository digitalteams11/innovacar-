package com.carrental.repository;

import com.carrental.entity.Role;
import com.carrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Login lookup — unique per (email, tenant) pair. */
    Optional<User> findByEmailAndTenantId(String email, Long tenantId);

    /** Standard Spring Security lookup (tenantId resolved from JWT). */
    Optional<User> findByEmail(String email);

    /** All users belonging to a tenant — used by admin list endpoint. */
    List<User> findAllByTenantId(Long tenantId);

    /** All users of a given role within a tenant. */
    List<User> findAllByTenantIdAndRole(Long tenantId, Role role);

    boolean existsByEmailAndTenantId(String email, Long tenantId);

    /** Tenant-scoped existence check by id — prevents cross-tenant access. */
    boolean existsByIdAndTenantId(Long id, Long tenantId);

    /** Tenant-scoped findById — prevents cross-tenant data leak. */
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);
}
