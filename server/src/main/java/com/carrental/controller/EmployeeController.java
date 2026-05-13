package com.carrental.controller;

import com.carrental.dto.employee.CreateEmployeeRequest;
import com.carrental.dto.employee.UpdateEmployeeRequest;
import com.carrental.dto.employee.EmployeeResponse;
import com.carrental.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Employee-management REST controller.
 *
 * <pre>
 * GET    /api/employees              – list all employees          [authenticated]
 * GET    /api/employees/{id}         – get employee by id          [authenticated]
 * POST   /api/employees              – create employee             [ADMIN]
 * PUT    /api/employees/{id}         – partial update              [ADMIN]
 * DELETE /api/employees/{id}         – delete employee             [ADMIN]
 * </pre>
 *
 * All endpoints sit behind the {@code JwtAuthenticationFilter} — an invalid or
 * missing token will never reach the controller.
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    // ── GET /api/employees ───────────────────────────────────────────────────

    /**
     * Returns all employees in the caller's tenant.
     */
    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> listEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    // ── GET /api/employees/{id} ──────────────────────────────────────────────

    /**
     * Fetches a single employee. Returns 404 for employees belonging to other
     * tenants (prevents cross-tenant enumeration).
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getEmployee(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    // ── POST /api/employees ──────────────────────────────────────────────────

    /**
     * Registers a new employee in the caller's tenant. ADMIN-only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createEmployee(request));
    }

    // ── PUT /api/employees/{id} ──────────────────────────────────────────────

    /**
     * Partially updates an employee. Only non-null fields are applied.
     * ADMIN-only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, request));
    }

    // ── DELETE /api/employees/{id} ───────────────────────────────────────────

    /**
     * Hard-deletes an employee. ADMIN-only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
