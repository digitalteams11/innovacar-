package com.carrental.entity;

/**
 * Lifecycle of a "client self-fill information" request. Deliberately a
 * smaller set than a full DRAFT/SENT/OPENED/.../CORRECTION_REQUESTED
 * machine — this is the MVP slice (see ClientInformationRequestService):
 * no draft-save, no open-tracking, no correction round-trip yet.
 */
public enum ClientInfoRequestStatus {
    SENT,       // Link created and shareable; client has not submitted yet.
    SUBMITTED,  // Client submitted their information; awaiting admin review.
    APPROVED,   // Admin approved — client created/linked, request is terminal.
    EXPIRED,    // Past expiresAt with no submission (or submitted-but-unreviewed link expiring).
    REVOKED     // Admin manually revoked the link before it was used.
}
