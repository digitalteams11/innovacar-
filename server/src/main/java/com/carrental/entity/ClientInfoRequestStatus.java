package com.carrental.entity;

/**
 * Lifecycle of a "client self-fill information" request.
 */
public enum ClientInfoRequestStatus {
    SENT,       // Link created and (best-effort) delivered; client has not opened or submitted yet.
    OPENED,     // Client opened the link but has not submitted yet.
    SUBMITTED,  // Client submitted their information; awaiting admin review.
    APPROVED,   // Admin approved — client created/linked, request is terminal.
    REJECTED,   // Admin reviewed the submission and rejected it — terminal, no client created.
    EXPIRED,    // Past expiresAt with no submission (or submitted-but-unreviewed link expiring).
    REVOKED     // Admin manually cancelled the link before it was used ("Cancel request").
}
