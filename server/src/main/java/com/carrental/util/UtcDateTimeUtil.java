package com.carrental.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Every naked {@link LocalDateTime} written by this codebase (entity
 * {@code @PrePersist} hooks, {@code LocalDateTime.now()} call sites, etc.)
 * represents UTC wall-clock time — see e.g. {@code ReportGenerationService}'s
 * {@code LocalDateTime.now(ZoneOffset.UTC)} and {@code ReportPeriodResolver}'s
 * explicit UTC round-trips. That convention was never applied when such a
 * value is serialized to JSON: {@code LocalDateTime.toString()} (and Jackson's
 * default JSR-310 serialization) both emit an offset-less string like
 * {@code "2026-08-07T10:15:30.123456"}. A browser parsing that string with
 * {@code new Date(...)} treats a timezone-less date-time string as *local*
 * time per the ECMA-262 spec — silently shifting every timestamp by the
 * viewer's UTC offset (the exact cause of a 2-minute-old notification
 * displaying as "1 hour ago" for a UTC+1 browser).
 *
 * <p>This is the single place that closes the gap: it explicitly stamps the
 * UTC offset before the value ever leaves the server, so the frontend can
 * parse it unambiguously with {@code new Date(...)}.
 */
public final class UtcDateTimeUtil {

    private UtcDateTimeUtil() {}

    /** Formats a UTC-wall-clock {@link LocalDateTime} as an unambiguous ISO-8601 instant, e.g. {@code "2026-08-07T10:15:30.123Z"}. */
    public static String toIsoUtc(LocalDateTime dt) {
        return dt == null ? null : dt.atOffset(ZoneOffset.UTC).toString();
    }
}
