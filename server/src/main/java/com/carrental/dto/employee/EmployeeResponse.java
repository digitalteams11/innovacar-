package com.carrental.dto.employee;

import com.carrental.entity.Employee;
import com.carrental.entity.EmployeeStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Read-only employee projection returned by all employee endpoints.
 */
@Data
@Builder
public class EmployeeResponse {

    private Long          id;
    private String        name;
    private String        email;
    private String        phone;
    private String        role;
    private String        department;
    private LocalDate     hireDate;
    private EmployeeStatus status;
    private Long          tenantId;

    // ── Static factory ───────────────────────────────────────────────────────

    public static EmployeeResponse from(Employee employee) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .name(employee.getName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .role(employee.getRole())
                .department(employee.getDepartment())
                .hireDate(employee.getHireDate())
                .status(employee.getStatus())
                .tenantId(employee.getTenant().getId())
                .build();
    }
}
