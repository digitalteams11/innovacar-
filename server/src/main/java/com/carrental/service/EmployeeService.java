package com.carrental.service;

import com.carrental.dto.employee.CreateEmployeeRequest;
import com.carrental.dto.employee.UpdateEmployeeRequest;
import com.carrental.dto.employee.EmployeeResponse;
import com.carrental.entity.Employee;
import com.carrental.entity.EmployeeStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.EmployeeRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Employee-management business logic.
 *
 * <p><strong>Tenant isolation:</strong> every query is scoped to the
 * {@code tenantId} extracted from the JWT via {@link TenantContext}.
 * A user of tenant A will always receive a 404 for employees that
 * belong to tenant B — preventing both data leakage and enumeration.
 *
 * <p><strong>Access policy (enforced at controller level):</strong>
 * Any authenticated user may read employees. Only ADMIN users may
 * create, update, or delete them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TenantRepository   tenantRepository;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all employees for the caller's tenant.
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAllEmployees() {
        Long tenantId = TenantContext.getCurrentTenantId();
        log.debug("Listing employees for tenant [{}]", tenantId);

        return employeeRepository.findAllByTenantId(tenantId)
                .stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    /**
     * Fetches a single employee scoped to the caller's tenant.
     *
     * @throws ResourceNotFoundException if the employee does not exist in this tenant
     */
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Long id) {
        return EmployeeResponse.from(fetchEmployeeInTenant(id));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Adds a new employee to the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the tenant record cannot be found
     */
    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        Long   tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant   = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        EmployeeStatus status = request.getStatus() != null
                ? request.getStatus()
                : EmployeeStatus.ACTIVE;

        Employee employee = employeeRepository.save(Employee.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(request.getRole())
                .department(request.getDepartment())
                .hireDate(request.getHireDate())
                .status(status)
                .tenant(tenant)
                .build());

        log.info("Created employee [id={}] '{}' in tenant [{}]",
                employee.getId(), employee.getName(), tenantId);

        return EmployeeResponse.from(employee);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields in {@code request} are applied.
     * ADMIN-only.
     *
     * @throws ResourceNotFoundException if the employee is not found in this tenant
     */
    @Transactional
    public EmployeeResponse updateEmployee(Long id, UpdateEmployeeRequest request) {
        Employee employee = fetchEmployeeInTenant(id);

        if (StringUtils.hasText(request.getName())) {
            employee.setName(request.getName());
        }
        if (StringUtils.hasText(request.getEmail())) {
            employee.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getPhone())) {
            employee.setPhone(request.getPhone());
        }
        if (StringUtils.hasText(request.getRole())) {
            employee.setRole(request.getRole());
        }
        if (StringUtils.hasText(request.getDepartment())) {
            employee.setDepartment(request.getDepartment());
        }
        if (request.getHireDate() != null) {
            employee.setHireDate(request.getHireDate());
        }
        if (request.getStatus() != null) {
            employee.setStatus(request.getStatus());
        }

        Employee saved = employeeRepository.save(employee);
        log.info("Updated employee [id={}] in tenant [{}]", id, TenantContext.getCurrentTenantId());
        return EmployeeResponse.from(saved);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes an employee from the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the employee is not found in this tenant
     */
    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = fetchEmployeeInTenant(id);
        employeeRepository.delete(employee);
        log.info("Deleted employee [id={}] from tenant [{}]",
                id, TenantContext.getCurrentTenantId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tenant-scoped employee lookup. Returns 404 for both missing and
     * cross-tenant employees so tenant B cannot discover tenant A's IDs.
     */
    private Employee fetchEmployeeInTenant(Long employeeId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return employeeRepository.findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + employeeId));
    }
}
