package com.carrental.exception;

import lombok.Getter;

import java.time.LocalDate;

/**
 * Thrown when a vehicle genuinely overlaps with another active reservation
 * or contract for the requested period. Maps to HTTP 409 with a precise
 * errorCode/field/conflict-detail payload — never used for same-reservation/
 * same-contract idempotent re-submissions, which return the existing
 * resource with 200 instead.
 */
@Getter
public class VehicleConflictException extends RuntimeException {
    private final String errorCode;
    private final String field;
    private Long vehicleId;
    private String conflictType;
    private LocalDate requestedStartDate;
    private LocalDate requestedEndDate;
    private String conflictSource;
    private Long conflictId;
    private LocalDate conflictStartDate;
    private LocalDate conflictEndDate;
    private String conflictNumber;
    private String conflictStatus;

    public VehicleConflictException(String message, String errorCode, String field) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    public VehicleConflictException(String message, String errorCode, String field, Long vehicleId, String conflictType) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
        this.vehicleId = vehicleId;
        this.conflictType = conflictType;
    }

    public VehicleConflictException(
            String message, String errorCode, String field, Long vehicleId, String conflictType,
            LocalDate requestedStartDate, LocalDate requestedEndDate,
            String conflictSource, Long conflictId, LocalDate conflictStartDate, LocalDate conflictEndDate) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
        this.vehicleId = vehicleId;
        this.conflictType = conflictType;
        this.requestedStartDate = requestedStartDate;
        this.requestedEndDate = requestedEndDate;
        this.conflictSource = conflictSource;
        this.conflictId = conflictId;
        this.conflictStartDate = conflictStartDate;
        this.conflictEndDate = conflictEndDate;
    }

    public VehicleConflictException(
            String message, String errorCode, String field, Long vehicleId, String conflictType,
            LocalDate requestedStartDate, LocalDate requestedEndDate,
            String conflictSource, Long conflictId, LocalDate conflictStartDate, LocalDate conflictEndDate,
            String conflictNumber, String conflictStatus) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
        this.vehicleId = vehicleId;
        this.conflictType = conflictType;
        this.requestedStartDate = requestedStartDate;
        this.requestedEndDate = requestedEndDate;
        this.conflictSource = conflictSource;
        this.conflictId = conflictId;
        this.conflictStartDate = conflictStartDate;
        this.conflictEndDate = conflictEndDate;
        this.conflictNumber = conflictNumber;
        this.conflictStatus = conflictStatus;
    }
}
