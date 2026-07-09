package com.carrental.dto.employee;

import com.carrental.entity.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/employees} — create an employee.
 */
@Data
public class CreateEmployeeRequest {

    private String name;

    private String fullName;

    @Email(message = "Must be a valid email address")
    private String email;

    private String phone;

    private String role;

    private String roleCode;

    private String department;

    private LocalDate hireDate;

    private EmployeeStatus status;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String temporaryPassword;
}
