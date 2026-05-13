package com.carrental.repository;

import com.carrental.entity.Employee;
import com.carrental.entity.EmployeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** All employees belonging to a tenant. */
    List<Employee> findAllByTenantId(Long tenantId);

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Employee> findByIdAndTenantId(Long id, Long tenantId);

    /** All employees of a specific status within a tenant — used for filtering. */
    List<Employee> findAllByTenantIdAndStatus(Long tenantId, EmployeeStatus status);
}
