package com.carrental.dto.contract;

/**
 * Optional body for POST /api/contracts/{id}/restore.
 *
 * <p><b>mode = "NORMAL"</b> (default): performs full availability check and
 * restores to the contract's previous status. Returns 409 if another active
 * booking conflicts with the same vehicle and date range.
 *
 * <p><b>mode = "DRAFT_ONLY"</b>: skips all availability checks and restores
 * the contract to DRAFT status. The vehicle is not reserved or blocked.
 * Use this to recover a contract for historical reference or editing without
 * making it an active booking.
 *
 * <p>Uses a plain String field (not an inner enum) so Jackson can always
 * deserialize it without any inner-class resolution issues.
 */
public class RestoreContractRequest {

    private String mode;

    public RestoreContractRequest() {}

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /** Returns true only when mode is exactly "DRAFT_ONLY" (case-insensitive). */
    public boolean isDraftOnly() {
        return "DRAFT_ONLY".equalsIgnoreCase(mode);
    }
}
