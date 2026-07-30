package com.carrental.service.reporting;

import com.carrental.entity.ReportType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPeriodResolverTest {

    private final ReportPeriodResolver resolver = new ReportPeriodResolver();

    @Test
    void previousClosedMonth_generatedFirstOfAugust_coversJulyInUtc() {
        ReportPeriodResolver.Period period = resolver.previousClosedMonth(ZoneOffset.UTC, LocalDate.of(2026, 8, 1));

        assertThat(period.start()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(period.end()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
    }

    @Test
    void previousClosedYear_generatedJan2_coversPreviousCalendarYear() {
        ReportPeriodResolver.Period period = resolver.previousClosedYear(ZoneOffset.UTC, LocalDate.of(2027, 1, 2));

        assertThat(period.start()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(period.end()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
    }

    @Test
    void previousClosedMonth_convertsAgencyLocalMidnightToUtc() {
        // A fixed UTC+1 zone (no DST) so local midnight on July 1st is 2026-06-30T23:00 UTC —
        // the stored boundary must reflect the agency's own local calendar, not UTC's.
        ZoneId fixedOffsetZone = ZoneOffset.ofHours(1);
        ReportPeriodResolver.Period period = resolver.previousClosedMonth(fixedOffsetZone, LocalDate.of(2026, 8, 1));

        assertThat(period.start()).isEqualTo(LocalDateTime.of(2026, 6, 30, 23, 0));
        assertThat(period.end()).isEqualTo(LocalDateTime.of(2026, 7, 31, 23, 0));
    }

    @Test
    void previousPeriod_forMonthlyReport_returnsCalendarMonthBeforeCurrentPeriod() {
        ReportPeriodResolver.Period currentPeriod = resolver.previousClosedMonth(ZoneOffset.UTC, LocalDate.of(2026, 8, 1));

        ReportPeriodResolver.Period previous = resolver.previousPeriod(ReportType.MONTHLY, currentPeriod, ZoneOffset.UTC);

        assertThat(previous.start()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(previous.end()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    void previousPeriod_forYearlyReport_returnsPreviousCalendarYear() {
        ReportPeriodResolver.Period currentPeriod = resolver.previousClosedYear(ZoneOffset.UTC, LocalDate.of(2027, 1, 2));

        ReportPeriodResolver.Period previous = resolver.previousPeriod(ReportType.YEARLY, currentPeriod, ZoneOffset.UTC);

        assertThat(previous.start()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));
        assertThat(previous.end()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void resolveZone_fallsBackToUtcForBlankOrInvalidTimezone() {
        assertThat(resolver.resolveZone(null)).isEqualTo(ZoneOffset.UTC);
        assertThat(resolver.resolveZone("")).isEqualTo(ZoneOffset.UTC);
        assertThat(resolver.resolveZone("Not/AZone")).isEqualTo(ZoneOffset.UTC);
        assertThat(resolver.resolveZone("Europe/Paris")).isEqualTo(ZoneId.of("Europe/Paris"));
    }
}
