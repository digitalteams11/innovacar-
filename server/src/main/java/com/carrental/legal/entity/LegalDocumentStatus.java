package com.carrental.legal.entity;

/** Lifecycle of one {@link LegalDocumentVersion} row. */
public enum LegalDocumentStatus {
    /** Being drafted by a Super Admin; not visible to end users. */
    DRAFT,
    /** The single live version end users see and must accept for its (type, locale). */
    PUBLISHED,
    /** A formerly published version, kept for audit/history purposes. */
    ARCHIVED
}
