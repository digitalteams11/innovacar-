package com.carrental.dto.employee;

import com.carrental.entity.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/employees} — create an employee.
 */
@Data
public class CreateEmployeeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    private String phone;

    private String role;

    private String department;

    private LocalDate hireDate;

    private EmployeeStatus status;
}
