package com.carrental.dto.employee;

import com.carrental.entity.EmployeeStatus;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for {@code PUT /api/employees/{id}} — partial update.
 * Any field left {@code null} is ignored by the service.
 */
@Data
public class UpdateEmployeeRequest {

    private String name;

    private String email;

    private String phone;

    private String role;

    private String department;

    private LocalDate hireDate;

    private EmployeeStatus status;
}
