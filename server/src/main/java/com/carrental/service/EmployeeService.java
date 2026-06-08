package com.carrental.service;

import com.carrental.dto.employee.CreateEmployeeRequest;
import com.carrental.dto.employee.UpdateEmployeeRequest;
import com.carrental.dto.employee.EmployeeResponse;
import com.carrental.entity.Employee;
import com.carrental.entity.EmployeeStatus;
import com.carrental.entity.Tenant;
import com.carrental.entity.Role;
import com.carrental.entity.User;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.EmployeeRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.UserRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        Role role = parseRole(request.getRole());

        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new IllegalArgumentException("Email already registered in this tenant");
        }

        String[] names = request.getName().trim().split("\\s+", 2);
        User user = userRepository.save(User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .firstName(names[0])
                .lastName(names.length > 1 ? names[1] : "")
                .phoneNumber(request.getPhone())
                .jobTitle(request.getDepartment())
                .accountEnabled(status == EmployeeStatus.ACTIVE)
                .tenant(tenant)
                .build());

        Employee employee = employeeRepository.save(Employee.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(role.name())
                .department(request.getDepartment())
                .hireDate(request.getHireDate() != null ? request.getHireDate() : java.time.LocalDate.now())
                .status(status)
                .user(user)
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
            if (employee.getUser() != null && !request.getEmail().equals(employee.getUser().getEmail())
                    && userRepository.existsByEmailAndTenantId(request.getEmail(), TenantContext.getCurrentTenantId())) {
                throw new IllegalArgumentException("Email already registered in this tenant");
            }
            employee.setEmail(request.getEmail());
            if (employee.getUser() != null) employee.getUser().setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getPhone())) {
            employee.setPhone(request.getPhone());
        }
        if (StringUtils.hasText(request.getRole())) {
            Role role = parseRole(request.getRole());
            employee.setRole(role.name());
            if (employee.getUser() != null) employee.getUser().setRole(role);
        }
        if (StringUtils.hasText(request.getDepartment())) {
            employee.setDepartment(request.getDepartment());
        }
        if (request.getHireDate() != null) {
            employee.setHireDate(request.getHireDate());
        }
        if (request.getStatus() != null) {
            employee.setStatus(request.getStatus());
            if (employee.getUser() != null) {
                employee.getUser().setAccountEnabled(request.getStatus() == EmployeeStatus.ACTIVE);
            }
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
        if (employee.getUser() != null) {
            employee.getUser().setAccountEnabled(false);
            userRepository.save(employee.getUser());
        }
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

    private Role parseRole(String value) {
        if (!StringUtils.hasText(value)) return Role.EMPLOYEE;
        try {
            Role role = Role.valueOf(value.trim().toUpperCase());
            if (role == Role.SUPER_ADMIN || role == Role.AGENCY_OWNER || role == Role.CLIENT) {
                throw new IllegalArgumentException("Role cannot be assigned to an employee");
            }
            return role;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid employee role: " + value);
        }
    }
}
