package com.carrental.controller;

import com.carrental.dto.maintenance.CreateMaintenanceRequest;
import com.carrental.dto.maintenance.MaintenanceResponse;
import com.carrental.entity.MaintenanceStatus;
import com.carrental.entity.User;
import com.carrental.exception.MaintenanceValidationException;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.service.VehicleMaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class VehicleMaintenanceController {
    private final VehicleMaintenanceService maintenanceService;

    @GetMapping
    @PreAuthorize("@rolePermissionService.has('VIEW_MAINTENANCE')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long vehicleId) {
        List<MaintenanceResponse> rows = maintenanceService.list(vehicleId);
        return ResponseEntity.ok(success("Maintenance work orders loaded successfully", rows));
    }

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('MANAGE_MAINTENANCE')")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateMaintenanceRequest request) {
        MaintenanceResponse response = maintenanceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("Maintenance work order created successfully", response, response.getWarnings()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@rolePermissionService.has('MANAGE_MAINTENANCE')")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User currentUser) {
        log.info("[MAINTENANCE_STATUS_CONTROLLER_HIT] id={} payload={} currentUser={}",
                id, body, currentUser != null ? currentUser.getEmail() : null);
        MaintenanceStatus status = parseStatus(body);
        MaintenanceResponse response = maintenanceService.updateStatus(id, status);
        return ResponseEntity.ok(success("Maintenance work order updated successfully", response, response.getWarnings()));
    }

    /**
     * Finds vehicles that are in MAINTENANCE status without an active work order and
     * auto-creates a SCHEDULED repair work order for each. Safe to call multiple times.
     */
    @PostMapping("/repair-missing-work-orders")
    @PreAuthorize("@rolePermissionService.has('MANAGE_MAINTENANCE')")
    public ResponseEntity<Map<String, Object>> repairMissingWorkOrders() {
        Map<String, Object> result = maintenanceService.repairMissingWorkOrders();
        return ResponseEntity.ok(success((String) result.get("message"), result));
    }

    /**
     * Returns how many vehicles are in MAINTENANCE status without an active work order.
     * The frontend uses this to decide whether to display the repair banner.
     */
    @GetMapping("/orphan-count")
    @PreAuthorize("@rolePermissionService.has('VIEW_MAINTENANCE')")
    public ResponseEntity<Map<String, Object>> orphanCount() {
        int count = maintenanceService.countOrphanedMaintenanceVehicles();
        return ResponseEntity.ok(success("Orphan count", Map.of("count", count)));
    }

    // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Exception handlers ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬

    @ExceptionHandler(MaintenanceValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMaintenanceValidation(MaintenanceValidationException ex) {
        return ResponseEntity.status(statusFor(ex.getErrorCode())).body(error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().isEmpty()
                ? null
                : ex.getBindingResult().getFieldErrors().get(0).getField();
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "Maintenance request is invalid."
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        String errorCode = switch (field == null ? "" : field) {
            case "vehicleId" -> "VEHICLE_REQUIRED";
            case "title" -> "TITLE_REQUIRED";
            case "scheduledDate" -> "INVALID_SCHEDULED_DATE";
            case "cost" -> "INVALID_COST";
            case "mileage" -> "INVALID_MILEAGE";
            default -> "MAINTENANCE_VALIDATION_ERROR";
        };
        return ResponseEntity.badRequest().body(error(message, errorCode));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        boolean dateRelated = ex.getCause() != null
                && ex.getCause().getClass().getSimpleName().contains("DateTime");
        String message = dateRelated
                ? "Invalid scheduled date. Use ISO format like 2026-06-15T14:53:00."
                : "Request body is invalid. Please check the submitted fields.";
        return ResponseEntity.badRequest()
                .body(error(message, dateRelated ? "INVALID_SCHEDULED_DATE" : "INVALID_REQUEST_BODY"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        // ResourceNotFoundException is shared across modules, so its message is
        // the only signal for which specific resource was missing here.
        String message = ex.getMessage() != null ? ex.getMessage() : "";
        String errorCode = message.toLowerCase().contains("vehicle") ? "VEHICLE_NOT_FOUND" : "MAINTENANCE_NOT_FOUND";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(ex.getMessage(), errorCode));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error(ex.getMessage(), "VEHICLE_NOT_IN_AGENCY"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error(ex.getMessage(), "MAINTENANCE_STATUS_CONFLICT"));
    }

    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<Map<String, Object>> handleLazy(LazyInitializationException ex) {
        log.error("[MAINTENANCE] LazyInitializationException ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â session was closed before response mapping", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("Unable to load related data for the maintenance record. Please try again.", "MAINTENANCE_LOAD_ERROR"));
    }

    @ExceptionHandler(JpaObjectRetrievalFailureException.class)
    public ResponseEntity<Map<String, Object>> handleJpaRetrieval(JpaObjectRetrievalFailureException ex) {
        log.error("[MAINTENANCE] JpaObjectRetrievalFailureException", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("Vehicle or maintenance relation was not found.", "VEHICLE_NOT_FOUND"));
    }

    @ExceptionHandler(org.hibernate.ObjectNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleHibernateObjectNotFound(org.hibernate.ObjectNotFoundException ex) {
        log.error("[MAINTENANCE] Hibernate ObjectNotFoundException Ã¢â‚¬â€ lazy proxy failed to load: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("Related entity was not found. The vehicle or maintenance record may have been deleted.", "VEHICLE_NOT_FOUND"));
    }

    @ExceptionHandler(org.springframework.transaction.TransactionSystemException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionSystem(org.springframework.transaction.TransactionSystemException ex) {
        log.error("[MAINTENANCE] TransactionSystemException during commit", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("Maintenance update could not be committed. Please try again.", "MAINTENANCE_COMMIT_FAILED"));
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNpe(NullPointerException ex) {
        log.error("[MAINTENANCE] NullPointerException", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("An unexpected null value was encountered. Check backend logs.", "MAINTENANCE_NPE"));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        log.warn("[MAINTENANCE] ConstraintViolationException: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(error("Maintenance request contains invalid values.", "MAINTENANCE_VALIDATION_ERROR"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("[MAINTENANCE] DataIntegrityViolationException", ex);
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (msg != null && msg.contains("vehicles_statut_check")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("Vehicle status value is not allowed by the database constraint. Run the latest migration so MAINTENANCE is allowed.", "VEHICLE_STATUS_CONSTRAINT_MISMATCH"));
        }
        if (msg != null && (msg.contains("vehicle_maintenance_status_check") || msg.contains("vehicle_maintenance"))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("Maintenance work order could not be saved because a database constraint rejected it.", "MAINTENANCE_DATA_CONFLICT"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("Maintenance work order conflicts with existing data. " + (msg != null && msg.length() < 120 ? msg : ""), "MAINTENANCE_DATA_CONFLICT"));
    }

    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDataAccess(InvalidDataAccessResourceUsageException ex) {
        log.error("[MAINTENANCE] InvalidDataAccessResourceUsageException", ex);
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("Maintenance database schema is not ready for this request. Run the latest migrations and try again.", "MAINTENANCE_SCHEMA_MISMATCH", Map.of("detail", msg)));
    }

    @ExceptionHandler(UnexpectedRollbackException.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedRollback(UnexpectedRollbackException ex, HttpServletRequest request) {
        String debugId = UUID.randomUUID().toString();
        boolean isStatusUpdate = isStatusEndpoint(request);
        // This means the maintenance transaction itself was marked rollback-only
        // by something inside it — with notifications now running in their own
        // REQUIRES_NEW transaction, this should no longer be triggered by a
        // notification failure. If it recurs, the real cause is logged here
        // instead of being swallowed into a generic 500.
        log.error("[MAINTENANCE_CREATE_FAILED_DEBUG] debugId={} endpoint={} rootCause={} message={}",
                debugId, request.getRequestURI(), ex.getClass().getName(), ex.getMessage(), ex);
        String errorCode = isStatusUpdate ? "MAINTENANCE_STATUS_UPDATE_FAILED" : "MAINTENANCE_TRANSACTION_ROLLBACK";
        String message = (isStatusUpdate
                ? "Unable to update maintenance status due to an unexpected transaction rollback. "
                : "Maintenance work order could not be committed due to an unexpected transaction rollback. ")
                + "Reference: " + debugId;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(message, errorCode, Map.of("debugId", debugId)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String debugId = UUID.randomUUID().toString();
        log.error("[MAINTENANCE_CREATE_FAILED_DEBUG] debugId={} endpoint={} rootCause={} message={}",
                debugId, request.getRequestURI(), ex.getClass().getName(), ex.getMessage(), ex);
        boolean isStatusUpdate = isStatusEndpoint(request);
        String errorCode = isStatusUpdate ? "MAINTENANCE_STATUS_UPDATE_FAILED" : "MAINTENANCE_CREATE_FAILED";
        String message = (isStatusUpdate
                ? "Unable to update maintenance status. Reference: "
                : "Unable to create maintenance work order. Reference: ") + debugId;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(message, errorCode, Map.of("debugId", debugId)));
    }

    private boolean isStatusEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/status");
    }

    // ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ Helpers ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬ÃƒÂ¢Ã¢â‚¬ÂÃ¢â€šÂ¬

    private HttpStatus statusFor(String errorCode) {
        if ("AUTH_REQUIRED".equals(errorCode))           return HttpStatus.UNAUTHORIZED;
        if ("AGENCY_CONTEXT_MISSING".equals(errorCode))  return HttpStatus.CONFLICT;
        if ("VEHICLE_NOT_IN_AGENCY".equals(errorCode))   return HttpStatus.FORBIDDEN;
        if ("PERMISSION_DENIED".equals(errorCode))       return HttpStatus.FORBIDDEN;
        if ("VEHICLE_NOT_FOUND".equals(errorCode))       return HttpStatus.NOT_FOUND;
        if ("VEHICLE_CURRENTLY_RENTED".equals(errorCode)) return HttpStatus.CONFLICT;
        return HttpStatus.BAD_REQUEST;
    }

    private MaintenanceStatus parseStatus(Map<String, String> body) {
        String status = body == null ? null : body.get("status");
        if (status == null || status.isBlank()) {
            throw new MaintenanceValidationException("Maintenance status is required", "STATUS_REQUIRED");
        }
        try {
            return MaintenanceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new MaintenanceValidationException("Maintenance status is invalid: " + status, "INVALID_MAINTENANCE_STATUS");
        }
    }

    private Map<String, Object> success(String message, Object data) {
        return Map.of("success", true, "message", message, "data", data);
    }

    private Map<String, Object> success(String message, Object data, List<String> warnings) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("warnings", warnings != null ? warnings : List.of());
        return response;
    }

    private Map<String, Object> error(String message, String errorCode) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("errorCode", errorCode);
        response.put("data", null);
        return response;
    }

    private Map<String, Object> error(String message, String errorCode, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("errorCode", errorCode);
        response.put("data", data);
        return response;
    }
}

