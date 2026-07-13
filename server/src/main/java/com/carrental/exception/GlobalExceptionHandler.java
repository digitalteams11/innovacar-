package com.carrental.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataResetSecurityException.class)
    public ResponseEntity<Map<String, Object>> handleDataResetSecurity(DataResetSecurityException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", "warning");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        response.put("status", ex.getStatus().value());
        response.put("error", ex.getStatus().getReasonPhrase());
        response.put("errorCode", ex.getErrorCode());
        response.put("message", ex.getMessage());
        response.put("data", ex.getData());
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error ->
                fieldErrors.put(((FieldError) error).getField(), error.getDefaultMessage()));
        return bodyWithCode(HttpStatus.BAD_REQUEST,
                fieldErrors.values().stream().findFirst().orElse("Please check the highlighted fields."),
                "warning", "VALIDATION_ERROR", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body", ex);
        return body(HttpStatus.BAD_REQUEST, "The submitted information could not be read.", "error", null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        String message = safeBusinessMessage(ex.getMessage(), "The requested item was not found.");
        return bodyWithCode(HttpStatus.NOT_FOUND, message, "error", notFoundErrorCode(message));
    }

    /**
     * Direct-create contract relies on this to tell the frontend exactly which
     * record was missing (CLIENT_NOT_FOUND vs VEHICLE_NOT_FOUND vs a generic
     * 404) instead of a single opaque "NOT_FOUND" code for every 404.
     */
    private String notFoundErrorCode(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("client")) return "CLIENT_NOT_FOUND";
        if (lower.contains("vehicle")) return "VEHICLE_NOT_FOUND";
        if (lower.contains("reservation")) return "RESERVATION_NOT_FOUND";
        if (lower.contains("contract")) return "CONTRACT_NOT_FOUND";
        if (lower.contains("tenant") || lower.contains("agency")) return "TENANT_NOT_FOUND";
        return "NOT_FOUND";
    }

    /**
     * Thrown by Hibernate when a lazy relation's row was deleted/soft-deleted
     * out from under a still-referencing record (e.g. a contract whose linked
     * vehicle was later removed). The raw message includes the fully
     * qualified entity class name ("Unable to find com.carrental.entity.X
     * with id N") — never forward that to the client; it leaks
     * implementation details and reads as a crash, not a clean error.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Related entity missing: {}", ex.getMessage());
        return bodyWithCode(HttpStatus.NOT_FOUND,
                "Related data is missing. Please refresh or repair this record.", "error", "RELATED_ENTITY_MISSING");
    }

    @ExceptionHandler(InspectionTokenExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleInspectionExpired(InspectionTokenExpiredException ex) {
        return bodyWithCode(HttpStatus.GONE,
                safeBusinessMessage(ex.getMessage(), "Inspection link expired."), "warning", "INSPECTION_TOKEN_EXPIRED");
    }

    @ExceptionHandler(InspectionUploadException.class)
    public ResponseEntity<Map<String, Object>> handleInspectionUpload(InspectionUploadException ex) {
        log.warn("Inspection upload failed: {}", ex.getMessage());
        return bodyWithCode(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to upload inspection photo", "error", "INSPECTION_UPLOAD_FAILED");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegal(IllegalArgumentException ex) {
        String message = safeBusinessMessage(ex.getMessage(), "This action could not be completed.");
        return bodyWithCode(HttpStatus.BAD_REQUEST,
                message, "warning", validationErrorCode(message));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, Object>> handleDateTimeParse(DateTimeParseException ex) {
        return bodyWithCode(HttpStatus.BAD_REQUEST,
                "Invalid date format. Use ISO format like 2026-06-15T14:53:00.", "warning", "INVALID_DATE_FORMAT");
    }

    /**
     * Catches any AuthenticationException subtype not already handled above
     * (BadCredentialsException, DisabledException, LockedException) so an
     * auth failure can never fall through to the generic 500 handler.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return bodyWithCode(HttpStatus.UNAUTHORIZED,
                "Your session has expired. Please sign in again.", "error", "UNAUTHORIZED");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return bodyWithCode(HttpStatus.METHOD_NOT_ALLOWED,
                "This HTTP method is not supported for this endpoint.", "warning", "METHOD_NOT_ALLOWED");
    }

    /**
     * Only fires when {@code spring.mvc.throw-exception-if-no-handler-found=true}
     * — otherwise an unmapped URL falls through to the container's whitelabel
     * HTML page instead of this JSON response.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex) {
        return bodyWithCode(HttpStatus.NOT_FOUND,
                "The requested endpoint does not exist.", "error", "ENDPOINT_NOT_FOUND");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        String message = safeBusinessMessage(ex.getMessage(), "This action is not available in the current state.");
        return body(HttpStatus.CONFLICT, message, "warning", conflictErrorCode(message));
    }

    private String conflictErrorCode(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("email already exists") || lower.contains("email already registered")) {
            return "EMAIL_ALREADY_EXISTS";
        }
        if (lower.contains("connect your gps provider")) {
            return "GPS_NOT_CONNECTED";
        }
        return null;
    }

    @ExceptionHandler(ClientValidationException.class)
    public ResponseEntity<Map<String, Object>> handleClientValidation(ClientValidationException ex) {
        return bodyWithCode(HttpStatus.BAD_REQUEST, ex.getMessage(), "warning", ex.getErrorCode());
    }

    /**
     * Normalized AI provider error — the raw provider exception/response never
     * reaches the client (see {@code com.carrental.service.provider.*ProviderClient}).
     */
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAiService(AiServiceException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "AI_LIMIT_REACHED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "AI_FEATURE_NOT_AVAILABLE" -> HttpStatus.FORBIDDEN;
            case "AI_PROVIDER_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "AI_DISABLED", "AI_CHAT_DISABLED",
                 "AI_KEY_NOT_CONFIGURED", "AI_NOT_CONFIGURED",
                 "AI_KEY_DECRYPTION_FAILED", "AI_INVALID_API_KEY",
                 "AI_SERVICE_UNAVAILABLE", "AI_MODEL_NOT_FOUND",
                 "AI_PROVIDER_ERROR", "AI_NO_ACTIVE_PROVIDER",
                 "AI_PROVIDER_DISABLED", "AI_MODEL_DISABLED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "AI_AUTOMATION_NOT_FOUND", "AI_PROVIDER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "AI_AUTOMATION_DISABLED", "AI_AUTOMATION_NOT_WIRED",
                 "AI_INVALID_CUSTOM_ENDPOINT" -> HttpStatus.BAD_REQUEST;
            // "In use" errors describe a conflict with the resource's current
            // state (still active / still referenced), not a malformed
            // request — 409 matches this codebase's convention elsewhere
            // (AdminLockoutException, VehicleConflictException, IllegalStateException).
            case "AI_PROVIDER_IN_USE" -> HttpStatus.CONFLICT;
            case "AI_CROSS_AGENCY_DENIED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return bodyWithCode(status, ex.getMessage(), "warning", ex.getErrorCode());
    }

    /**
     * A path/query parameter couldn't be converted to its target type (e.g. a
     * non-numeric {@code id} in {@code DELETE /api/.../providers/{id}}).
     * Without this handler, Spring's default resolver returns a bare 400
     * with no JSON body — indistinguishable from a real backend failure and
     * impossible for the frontend to explain to the user.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.warn("Invalid request parameter [{}={}]", ex.getName(), ex.getValue());
        return bodyWithCode(HttpStatus.BAD_REQUEST,
                "Invalid value for '" + ex.getName() + "'.", "warning", "INVALID_PATH_PARAMETER");
    }

    @ExceptionHandler(PlanLimitException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimit(PlanLimitException ex) {
        Map<String, Object> response = new HashMap<>(ex.toDetail());
        response.put("success", false);
        response.put("severity", "warning");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        if ("EMPLOYEES".equalsIgnoreCase(ex.getResource())) {
            response.put("errorCode", "EMPLOYEE_LIMIT_REACHED");
            response.put("message", "Your current plan allows only " + ex.getLimit() + " employees. Upgrade to add more.");
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(PaidAmountExceedsTotalException.class)
    public ResponseEntity<Map<String, Object>> handlePaidAmountExceedsTotal(PaidAmountExceedsTotalException ex) {
        Map<String, Object> data = new HashMap<>();
        data.put("paidAmount", ex.getPaidAmount());
        data.put("totalAmount", ex.getTotalAmount());
        data.put("maxAllowed", ex.getTotalAmount());
        data.put("suggestedField", "depositAmount");
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", "warning");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        response.put("errorCode", "PAID_AMOUNT_EXCEEDS_TOTAL");
        response.put("message", "Rental payment cannot exceed contract total. Use Caution / Garantie for refundable deposit.");
        response.put("data", data);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(VehicleConflictException.class)
    public ResponseEntity<Map<String, Object>> handleVehicleConflict(VehicleConflictException ex) {
        Map<String, Object> data = new HashMap<>();
        if (ex.getField() != null) {
            data.put("field", ex.getField());
        }
        if (ex.getVehicleId() != null) {
            data.put("vehicleId", ex.getVehicleId());
        }
        if (ex.getConflictType() != null) {
            data.put("conflictType", ex.getConflictType());
        }
        if (ex.getRequestedStartDate() != null) {
            data.put("requestedStartDate", ex.getRequestedStartDate());
        }
        if (ex.getRequestedEndDate() != null) {
            data.put("requestedEndDate", ex.getRequestedEndDate());
        }
        if (ex.getConflictSource() != null) {
            data.put("conflictSource", ex.getConflictSource());
        }
        if (ex.getConflictId() != null) {
            data.put("conflictId", ex.getConflictId());
        }
        if (ex.getConflictStartDate() != null) {
            data.put("conflictStartDate", ex.getConflictStartDate());
        }
        if (ex.getConflictEndDate() != null) {
            data.put("conflictEndDate", ex.getConflictEndDate());
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", "warning");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        response.put("errorCode", ex.getErrorCode());
        response.put("message", ex.getMessage());
        response.put("data", data.isEmpty() ? null : data);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ClientDuplicateException.class)
    public ResponseEntity<Map<String, Object>> handleClientDuplicate(ClientDuplicateException ex) {
        Map<String, Object> data = new HashMap<>();
        if (ex.getField() != null) data.put("field", ex.getField());
        if (ex.getExistingClientId() != null) data.put("existingClientId", ex.getExistingClientId());
        if (ex.getExistingClientName() != null) data.put("existingClientName", ex.getExistingClientName());
        if (ex.getExistingClientPhone() != null) data.put("existingClientPhone", ex.getExistingClientPhone());
        if (ex.getMatchedFields() != null && !ex.getMatchedFields().isEmpty()) data.put("matchedFields", ex.getMatchedFields());
        data.put("canUseExistingClient", ex.getExistingClientId() != null);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", "warning");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        response.put("errorCode", ex.getErrorCode());
        response.put("message", ex.getMessage());
        response.put("field", ex.getField());
        response.put("data", data.isEmpty() ? null : data);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        jakarta.validation.ConstraintViolation::getMessage,
                        (left, right) -> left));
        return bodyWithCode(HttpStatus.BAD_REQUEST,
                fieldErrors.values().stream().findFirst().orElse("Please check the highlighted fields."),
                "warning", "VALIDATION_ERROR", fieldErrors);
    }

    @ExceptionHandler(com.carrental.exception.TokenRefreshException.class)
    public ResponseEntity<Map<String, Object>> handleTokenRefresh(com.carrental.exception.TokenRefreshException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", "error");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        response.put("errorCode", "SESSION_EXPIRED");
        response.put("message", ex.getMessage());
        response.put("data", null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", "error");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        response.put("errorCode", "INVALID_CREDENTIALS");
        response.put("message", "Invalid email or password");
        response.put("data", null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(TwoFactorVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleTwoFactorVerification(TwoFactorVerificationException ex) {
        return bodyWithCode(HttpStatus.UNAUTHORIZED, ex.getMessage(), "warning", ex.getErrorCode());
    }

    @ExceptionHandler(TwoFactorRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleTwoFactorRequired(TwoFactorRequiredException ex) {
        Map<String, Object> details = new HashMap<>();
        details.put("twoFactorRequired", true);
        details.put("twoFactorMethod", ex.getMethod() == null ? "AUTHENTICATOR" : ex.getMethod().name());
        return body(HttpStatus.PRECONDITION_REQUIRED,
                "Enter the verification code from your authenticator app.", "warning", details);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(DisabledException ex) {
        return body(HttpStatus.FORBIDDEN, "This account is currently disabled.", "error", null);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Map<String, Object>> handleLocked(LockedException ex) {
        return body(HttpStatus.LOCKED,
                safeBusinessMessage(ex.getMessage(), "This account is temporarily locked."), "error", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return bodyWithCode(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", "error", "PERMISSION_DENIED");
    }

    @ExceptionHandler(AdminLockoutException.class)
    public ResponseEntity<Map<String, Object>> handleAdminLockout(AdminLockoutException ex) {
        return bodyWithCode(HttpStatus.CONFLICT, ex.getMessage(), "warning", "ADMIN_LOCKOUT_PREVENTED");
    }

    @ExceptionHandler(TemplatePlanRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleTemplatePlanRequired(TemplatePlanRequiredException ex) {
        Map<String, Object> data = new HashMap<>();
        data.put("requiredPlan", ex.getRequiredPlan());
        return bodyWithCode(HttpStatus.FORBIDDEN,
                safeBusinessMessage(ex.getMessage(), "Your plan does not include this contract template."),
                "warning", "TEMPLATE_PLAN_REQUIRED", data);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return bodyWithCode(HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file is too large.", "warning", "UPLOAD_TOO_LARGE");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException ex) {
        log.warn("Multipart request failed: {}", ex.getMessage());
        return bodyWithCode(HttpStatus.BAD_REQUEST,
                "Template file is required.", "warning", "MULTIPART_UPLOAD_ERROR");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Identify the exact DB column that failed instead of returning a
        // blanket "Client already exists" — that message previously fired for
        // *any* unique-constraint violation (contract number, QR token, the
        // contract<->reservation link, payment number, ...), which produced a
        // vague/wrong 409 on the Create Contract flow whenever a non-client
        // constraint was hit.
        String constraintName = extractConstraintName(ex);
        String offendingColumn = extractOffendingColumn(ex);
        log.warn("Data integrity violation [constraint={}] [column={}]", constraintName, offendingColumn, ex);

        String errorCode;
        String message;
        if ("contracts_contract_status_check".equals(constraintName)) {
            errorCode = "INVALID_CONTRACT_STATUS";
            message = "Contract status is not allowed by database constraint.";
        } else if ("vehicles_statut_check".equals(constraintName)) {
            errorCode = "VEHICLE_STATUS_CONSTRAINT_MISMATCH";
            message = "Vehicle status value is not allowed by database constraint.";
        } else if (offendingColumn != null && offendingColumn.contains("paid")
                && !offendingColumn.contains("payment")) {
            errorCode = "PAYMENT_REQUIRED_FIELD_MISSING";
            message = "Payment creation failed because required field 'paid' is missing.";
        } else if (offendingColumn != null && offendingColumn.contains("contract_number")) {
            errorCode = "CONTRACT_NUMBER_EXISTS";
            message = "Contract number already exists.";
        } else if (offendingColumn != null && offendingColumn.contains("reservation_id")) {
            errorCode = "RESERVATION_ALREADY_CONVERTED";
            message = "This reservation has already been converted to a contract.";
        } else if (offendingColumn != null && offendingColumn.contains("qr_token")) {
            errorCode = "QR_TOKEN_CONFLICT";
            message = "Could not generate a unique signing link. Please try again.";
        } else if (offendingColumn != null && offendingColumn.contains("payment_number")) {
            errorCode = "PAYMENT_NUMBER_CONFLICT";
            message = "Could not generate a unique payment reference. Please try again.";
        } else if (offendingColumn != null
                && (offendingColumn.contains("email") || offendingColumn.contains("cin")
                        || offendingColumn.contains("phone") || offendingColumn.contains("license")
                        || offendingColumn.contains("passport"))) {
            errorCode = "CLIENT_ALREADY_EXISTS";
            message = "Client already exists";
        } else if (constraintName != null) {
            // Known constraint name but unrecognized column — report it clearly
            errorCode = "DATA_CONFLICT";
            message = "This action conflicts with existing data. [constraint: " + constraintName + "]";
            log.error("Unhandled DB constraint [{}] — add a specific case to handleDataIntegrity()", constraintName);
        } else {
            errorCode = "DATA_CONFLICT";
            message = "This action conflicts with existing data.";
        }

        Map<String, Object> data = new HashMap<>();
        if ("PAYMENT_REQUIRED_FIELD_MISSING".equals(errorCode)) data.put("field", "paid");
        if ("INVALID_CONTRACT_STATUS".equals(errorCode)) {
            data.put("hint", "Check contract_status value and allowed ContractStatus enum values.");
            data.put("contractStatus", null);
        }
        if ("VEHICLE_STATUS_CONSTRAINT_MISMATCH".equals(errorCode)) {
            data.put("hint", "Update VehicleStatus enum and vehicles_statut_check migration so they match.");
        }
        if (constraintName != null) data.put("constraint", constraintName);
        if (offendingColumn != null) data.put("column", offendingColumn);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", "warning");
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        response.put("errorCode", errorCode);
        response.put("message", message);
        response.put("data", data.isEmpty() ? null : data);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    private String extractConstraintName(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private static final java.util.regex.Pattern KEY_COL_PATTERN =
            java.util.regex.Pattern.compile("Key \\(([a-zA-Z0-9_, ]+)\\)=");
    private static final java.util.regex.Pattern NULL_COL_PATTERN =
            java.util.regex.Pattern.compile("null value in column \"([a-zA-Z0-9_]+)\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Extracts the offending DB column from the exception chain.
     * Checks both {@code getMessage()} (which PostgreSQL JDBC 42+ includes the
     * Detail line in) AND the raw {@code ServerErrorMessage.getDetail()} field
     * via reflection (runtime-scoped PostgreSQL driver).
     */
    private String extractOffendingColumn(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            String col = matchColumn(cause.getMessage());
            if (col != null) return col;

            // PostgreSQL JDBC keeps the Detail in a separate field not always
            // present in getMessage() — extract it via reflection to avoid a
            // compile-time dependency on the runtime-scoped driver jar.
            if (cause instanceof java.sql.SQLException) {
                try {
                    java.lang.reflect.Method getSem = cause.getClass().getMethod("getServerErrorMessage");
                    Object sem = getSem.invoke(cause);
                    if (sem != null) {
                        java.lang.reflect.Method getDetail = sem.getClass().getMethod("getDetail");
                        col = matchColumn((String) getDetail.invoke(sem));
                        if (col != null) return col;
                        java.lang.reflect.Method getMessage = sem.getClass().getMethod("getMessage");
                        col = matchColumn((String) getMessage.invoke(sem));
                        if (col != null) return col;
                    }
                } catch (Exception ignored) { /* not a PSQLException */ }
            }
            cause = cause.getCause();
        }
        return null;
    }

    private String matchColumn(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = KEY_COL_PATTERN.matcher(text);
        if (m.find()) return m.group(1).toLowerCase(java.util.Locale.ROOT);
        m = NULL_COL_PATTERN.matcher(text);
        if (m.find()) return m.group(1).toLowerCase(java.util.Locale.ROOT);
        return null;
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<Map<String, Object>> handleFileStorage(UncheckedIOException ex) {
        log.error("File storage failed", ex);
        return bodyWithCode(HttpStatus.INTERNAL_SERVER_ERROR,
                safeBusinessMessage(ex.getMessage(), "Unable to save file. Upload folder could not be created."),
                "error", "TEMPLATE_FILE_STORAGE_FAILED");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("Unhandled exception [requestId={}] [path={}]", requestId, request.getRequestURI(), ex);
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", "error");
        response.put("requestId", requestId);
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        response.put("errorCode", "INTERNAL_SERVER_ERROR");
        response.put("message", "The service is temporarily unavailable. Please try again later.");
        response.put("path", request.getRequestURI());
        response.put("data", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private ResponseEntity<Map<String, Object>> body(
            HttpStatus status, String message, String severity, Object details) {
        return body(status, message, severity, details, UUID.randomUUID().toString());
    }

    private ResponseEntity<Map<String, Object>> bodyWithCode(
            HttpStatus status, String message, String severity, String errorCode) {
        return bodyWithCode(status, message, severity, errorCode, null);
    }

    private ResponseEntity<Map<String, Object>> bodyWithCode(
            HttpStatus status, String message, String severity, String errorCode, Object details) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", severity);
        response.put("requestId", UUID.randomUUID().toString());
        response.put("timestamp", Instant.now().toString());
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("errorCode", errorCode);
        response.put("message", message);
        response.put("data", null);
        if (details != null) response.put("details", details);
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<Map<String, Object>> body(
            HttpStatus status, String message, String severity, Object details, String requestId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("severity", severity);
        response.put("requestId", requestId);
        response.put("timestamp", Instant.now().toString());
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("errorCode", status.name());
        response.put("message", message);
        response.put("data", null);
        if (details != null) response.put("details", details);
        return ResponseEntity.status(status).body(response);
    }

    private String safeBusinessMessage(String message, String fallback) {
        if (message == null || message.isBlank() || message.length() > 220) return fallback;
        String lower = message.toLowerCase();
        if (lower.contains("exception") || lower.contains("sql") || lower.contains("jdbc")
                || lower.contains("hibernate") || lower.contains("nullpointer")
                || lower.contains("database") || lower.contains("constraint")
                || lower.contains("stack trace")) {
            return fallback;
        }
        return message;
    }

    private String validationErrorCode(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("base url is invalid")) {
            return "GPS_INVALID_BASE_URL";
        }
        if (lower.contains("api key is required to connect this gps provider")) {
            return "GPS_API_KEY_REQUIRED";
        }
        if (lower.contains("pdf") && lower.contains("jfif")) {
            return "INVALID_TEMPLATE_FILE_TYPE";
        }
        if (lower.contains("file is required")) {
            return "TEMPLATE_FILE_REQUIRED";
        }
        if (lower.contains("too large")) {
            return "UPLOAD_TOO_LARGE";
        }
        if (lower.contains("agency context missing")) {
            return "AGENCY_CONTEXT_MISSING";
        }
        if ("weak_password".equals(lower) || lower.contains("weak_password")) {
            return "WEAK_PASSWORD";
        }
        if (lower.contains("invalid employee role")) {
            return "INVALID_ROLE";
        }
        if (lower.contains("employee email is required")) {
            return "VALIDATION_ERROR";
        }
        if (lower.contains("no agency")) {
            return "TENANT_REQUIRED";
        }
        if (lower.contains("no daily price")) {
            return "VEHICLE_PRICE_MISSING";
        }
        if (lower.contains("rental end must be after rental start") || lower.contains("rental start and end dates are required")) {
            return "INVALID_DATE_RANGE";
        }
        return "BUSINESS_VALIDATION_ERROR";
    }
}





