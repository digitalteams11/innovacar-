package com.carrental.exception;

/**
 * Thrown when a permanent vehicle purge is attempted while a contract or
 * reservation row still references the vehicle. The vehicle stays
 * soft-deleted in Trash; maps to HTTP 409 in the controller.
 */
public class VehicleStillReferencedException extends RuntimeException {
    public VehicleStillReferencedException(String message) {
        super(message);
    }
}
