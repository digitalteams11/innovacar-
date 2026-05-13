package com.carrental.repository;

import com.carrental.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /** All clients belonging to a tenant. */
    List<Client> findAllByTenantId(Long tenantId);

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Client> findByIdAndTenantId(Long id, Long tenantId);

    /** Existence check to guard delete / update. */
    boolean existsByIdAndTenantId(Long id, Long tenantId);
}
