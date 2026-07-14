package com.carrental.legal.audit;

import com.carrental.legal.entity.LegalDocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Structured, greppable log lines for compliance-relevant legal-module
 * events. This is a supplement to the durable audit trails already kept as
 * rows (LegalAcceptance, PrivacyRequest) — those are the source of truth for
 * "who accepted/requested what and when"; this logger exists so admin
 * actions (drafting, publishing, resolving requests) are traceable in the
 * application log stream too, e.g. for incident review or CNDP inquiries.
 */
@Slf4j
@Component
public class LegalAuditLogger {

    public void documentDrafted(Long adminUserId, LegalDocumentType type, String locale, int versionNumber) {
        log.info("[LEGAL_AUDIT] action=DRAFT_CREATED adminUserId={} documentType={} locale={} versionNumber={}",
                adminUserId, type, locale, versionNumber);
    }

    public void documentUpdated(Long adminUserId, Long versionId) {
        log.info("[LEGAL_AUDIT] action=DRAFT_UPDATED adminUserId={} documentVersionId={}", adminUserId, versionId);
    }

    public void documentPublished(Long adminUserId, LegalDocumentType type, String locale, int versionNumber, boolean material) {
        log.info("[LEGAL_AUDIT] action=PUBLISHED adminUserId={} documentType={} locale={} versionNumber={} material={}",
                adminUserId, type, locale, versionNumber, material);
    }

    public void documentArchived(Long adminUserId, Long versionId) {
        log.info("[LEGAL_AUDIT] action=ARCHIVED adminUserId={} documentVersionId={}", adminUserId, versionId);
    }

    public void acceptanceRecorded(Long userId, LegalDocumentType type, int versionNumber, String context) {
        log.info("[LEGAL_AUDIT] action=ACCEPTED userId={} documentType={} versionNumber={} context={}",
                userId, type, versionNumber, context);
    }

    public void cookieConsentUpdated(String subjectKey, boolean functional, boolean analytics, boolean marketing) {
        log.info("[LEGAL_AUDIT] action=COOKIE_CONSENT_UPDATED subject={} functional={} analytics={} marketing={}",
                subjectKey, functional, analytics, marketing);
    }

    public void privacyRequestCreated(Long userId, Long requestId, String requestType) {
        log.info("[LEGAL_AUDIT] action=PRIVACY_REQUEST_CREATED userId={} requestId={} type={}", userId, requestId, requestType);
    }

    public void privacyRequestStatusChanged(Long adminUserId, Long requestId, String newStatus) {
        log.info("[LEGAL_AUDIT] action=PRIVACY_REQUEST_STATUS_CHANGED adminUserId={} requestId={} newStatus={}",
                adminUserId, requestId, newStatus);
    }
}
