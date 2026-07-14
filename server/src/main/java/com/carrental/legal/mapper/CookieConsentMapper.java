package com.carrental.legal.mapper;

import com.carrental.legal.dto.CookiePreferenceResponse;
import com.carrental.legal.entity.CookieConsent;

public final class CookieConsentMapper {

    private CookieConsentMapper() {
    }

    public static CookiePreferenceResponse toDto(CookieConsent consent) {
        if (consent == null) return null;
        return CookiePreferenceResponse.builder()
                .anonymousId(consent.getAnonymousId())
                .necessary(true)
                .functional(consent.isFunctional())
                .analytics(consent.isAnalytics())
                .marketing(consent.isMarketing())
                .policyVersionNumber(consent.getPolicyVersionNumber())
                .updatedAt(consent.getUpdatedAt())
                .choiceRecorded(true)
                .build();
    }
}
