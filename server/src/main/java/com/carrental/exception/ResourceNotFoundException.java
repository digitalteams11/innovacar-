package com.carrental.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a resource (user, tenant, …) cannot be found within the
 * caller's tenant scope. Maps to HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException user(Long id) {
        return new ResourceNotFoundException("User not found with id: " + id);
    }
}
