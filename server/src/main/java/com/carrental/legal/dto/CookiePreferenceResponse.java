package com.carrental.legal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CookiePreferenceResponse {
    private String anonymousId;
    @Builder.Default
    private boolean necessary = true;
    private boolean functional;
    private boolean analytics;
    private boolean marketing;
    private Integer policyVersionNumber;
    private LocalDateTime updatedAt;
    /** True if no choice has been recorded yet — the frontend should still show the cookie banner. */
    private boolean choiceRecorded;
}
