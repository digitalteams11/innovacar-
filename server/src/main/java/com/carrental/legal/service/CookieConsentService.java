package com.carrental.legal.service;

import com.carrental.entity.User;
import com.carrental.legal.audit.LegalAuditLogger;
import com.carrental.legal.dto.CookiePreferenceRequest;
import com.carrental.legal.dto.CookiePreferenceResponse;
import com.carrental.legal.entity.CookieConsent;
import com.carrental.legal.entity.LegalDocumentStatus;
import com.carrental.legal.entity.LegalDocumentType;
import com.carrental.legal.entity.LegalLocale;
import com.carrental.legal.mapper.CookieConsentMapper;
import com.carrental.legal.repository.CookieConsentRepository;
import com.carrental.legal.repository.LegalDocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cookie-category preferences. Works pre-login (keyed by an anonymous UUID
 * the frontend persists in a first-party cookie) and post-login (keyed by
 * user id, merged in from the anonymous record the first time an
 * authenticated request supplies one).
 */
@Service
@RequiredArgsConstructor
public class CookieConsentService {

    private final CookieConsentRepository cookieConsentRepository;
    private final LegalDocumentVersionRepository versionRepository;
    private final LegalAuditLogger auditLogger;

    @Transactional(readOnly = true)
    public CookiePreferenceResponse getPreferences(Long userId, String anonymousId) {
        CookieConsent consent = null;
        if (userId != null) {
            consent = cookieConsentRepository.findByUserId(userId).orElse(null);
        }
        if (consent == null && anonymousId != null && !anonymousId.isBlank()) {
            consent = cookieConsentRepository.findByAnonymousId(anonymousId).orElse(null);
        }
        if (consent == null) {
            return CookiePreferenceResponse.builder()
                    .anonymousId(anonymousId)
                    .necessary(true)
                    .functional(false)
                    .analytics(false)
                    .marketing(false)
                    .choiceRecorded(false)
                    .build();
        }
        return CookieConsentMapper.toDto(consent);
    }

    @Transactional
    public CookiePreferenceResponse savePreferences(Long userId, Long tenantId, CookiePreferenceRequest request, String ipAddress) {
        String anonymousId = request.getAnonymousId() != null && !request.getAnonymousId().isBlank()
                ? request.getAnonymousId()
                : (userId == null ? UUID.randomUUID().toString() : null);

        CookieConsent consent = null;
        if (userId != null) {
            consent = cookieConsentRepository.findByUserId(userId).orElse(null);
        }
        if (consent == null && anonymousId != null) {
            consent = cookieConsentRepository.findByAnonymousId(anonymousId).orElse(null);
        }
        if (consent == null) {
            consent = CookieConsent.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .anonymousId(anonymousId)
                    .build();
        } else if (userId != null && consent.getUserId() == null) {
            // Anonymous consent given pre-login is now claimed by the account that just logged in.
            consent.setUserId(userId);
            consent.setTenantId(tenantId);
        }

        consent.setFunctional(request.isFunctional());
        consent.setAnalytics(request.isAnalytics());
        consent.setMarketing(request.isMarketing());
        consent.setPolicyVersionNumber(currentCookiePolicyVersion());
        consent.setIpAddress(ipAddress);

        CookieConsent saved = cookieConsentRepository.save(consent);
        auditLogger.cookieConsentUpdated(
                userId != null ? "user:" + userId : "anon:" + anonymousId,
                saved.isFunctional(), saved.isAnalytics(), saved.isMarketing());
        return CookieConsentMapper.toDto(saved);
    }

    private Integer currentCookiePolicyVersion() {
        return versionRepository
                .findByDocumentTypeAndLocaleAndStatus(LegalDocumentType.COOKIE_POLICY, LegalLocale.FR, LegalDocumentStatus.PUBLISHED)
                .map(v -> v.getVersionNumber())
                .orElse(null);
    }

    /** Called at login to fold an anonymous pre-login cookie choice into the now-known user's record. */
    @Transactional
    public void mergeAnonymousIntoUser(User user, String anonymousId) {
        if (anonymousId == null || anonymousId.isBlank()) return;
        if (cookieConsentRepository.findByUserId(user.getId()).isPresent()) return;
        cookieConsentRepository.findByAnonymousId(anonymousId).ifPresent(consent -> {
            consent.setUserId(user.getId());
            consent.setTenantId(user.getTenant() != null ? user.getTenant().getId() : null);
            cookieConsentRepository.save(consent);
        });
    }
}
