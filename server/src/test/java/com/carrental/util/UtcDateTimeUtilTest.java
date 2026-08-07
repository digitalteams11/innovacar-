package com.carrental.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UtcDateTimeUtilTest {

    @Test
    void nullInputReturnsNull() {
        assertThat(UtcDateTimeUtil.toIsoUtc(null)).isNull();
    }

    @Test
    void stampsAnUnambiguousUtcOffset() {
        LocalDateTime dt = LocalDateTime.of(2026, 8, 7, 10, 15, 30);
        String iso = UtcDateTimeUtil.toIsoUtc(dt);
        // Must carry an explicit UTC marker so a browser's `new Date(...)`
        // never re-interprets it as local time — the root cause of the
        // "5-minute-old notification shows as 1 hour ago" bug.
        assertThat(iso).endsWith("Z");
        assertThat(iso).startsWith("2026-08-07T10:15:30");
    }
}
