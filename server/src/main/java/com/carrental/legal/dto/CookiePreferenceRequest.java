package com.carrental.legal.dto;

import lombok.Data;

/** "Necessary" is deliberately absent — it's always true and isn't a user choice. */
@Data
public class CookiePreferenceRequest {
    private String anonymousId;
    private boolean functional;
    private boolean analytics;
    private boolean marketing;
}
