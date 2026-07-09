package com.carrental.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a role-access change would remove the last way to administer
 * the agency (e.g. disabling ROLE_ACCESS_MANAGE or the last enabled
 * permission for the ADMIN/AGENCY_OWNER role). Maps to HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AdminLockoutException extends RuntimeException {
    public AdminLockoutException(String message) {
        super(message);
    }
}
