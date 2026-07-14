package com.carrental.legal.entity;

/**
 * The fixed catalogue of legal/compliance documents the platform publishes.
 * Each type has its own independent version history per {@link LegalLocale}.
 */
public enum LegalDocumentType {
    TERMS_OF_SERVICE,
    PRIVACY_POLICY,
    COOKIE_POLICY,
    ACCEPTABLE_USE_POLICY,
    SECURITY_POLICY,
    DATA_RETENTION_POLICY,
    SUBSCRIPTION_BILLING_TERMS,
    USER_RULES_AND_RESPONSIBILITIES,
    AGENCY_ADMIN_RESPONSIBILITIES,
    GPS_GEOLOCATION_NOTICE,
    AI_USAGE_NOTICE,
    ELECTRONIC_SIGNATURE_NOTICE,
    CONTRACT_DOCUMENT_RETENTION_NOTICE,
    DATA_PROCESSING_SUBPROCESSOR_NOTICE,
    ACCOUNT_DELETION_DATA_EXPORT_POLICY
}
