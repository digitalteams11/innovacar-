package com.carrental.service.reporting;

import com.carrental.entity.ReportType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Computes closed-period (previous complete month / previous complete year)
 * boundaries in the agency's own timezone, then converts to UTC for storage —
 * per spec section 3. All {@link Period} instants are start-inclusive,
 * end-exclusive.
 */
@Component
public class ReportPeriodResolver {

    public record Period(LocalDateTime start, LocalDateTime end) {}

    /** The complete previous calendar month relative to {@code referenceDate}, e.g. run on 2026-08-01 → 2026-07-01..2026-08-01. */
    public Period previousClosedMonth(ZoneId tenantZone, LocalDate referenceDate) {
        YearMonth previousMonth = YearMonth.from(referenceDate).minusMonths(1);
        return toUtcPeriod(tenantZone, previousMonth.atDay(1), previousMonth.plusMonths(1).atDay(1));
    }

    /** The complete previous calendar year relative to {@code referenceDate}, e.g. run on 2027-01-02 → 2026-01-01..2027-01-01. */
    public Period previousClosedYear(ZoneId tenantZone, LocalDate referenceDate) {
        int previousYear = referenceDate.getYear() - 1;
        return toUtcPeriod(tenantZone, LocalDate.of(previousYear, 1, 1), LocalDate.of(previousYear + 1, 1, 1));
    }

    /** The calendar period immediately preceding {@code currentPeriod}, for period-over-period comparison (spec section 7). */
    public Period previousPeriod(ReportType type, Period currentPeriod, ZoneId tenantZone) {
        LocalDate currentStartInZone = currentPeriod.start()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(tenantZone)
                .toLocalDate();
        if (type == ReportType.YEARLY) {
            int year = currentStartInZone.getYear();
            return toUtcPeriod(tenantZone, LocalDate.of(year - 1, 1, 1), LocalDate.of(year, 1, 1));
        }
        YearMonth currentMonth = YearMonth.from(currentStartInZone);
        YearMonth previousMonth = currentMonth.minusMonths(1);
        return toUtcPeriod(tenantZone, previousMonth.atDay(1), currentMonth.atDay(1));
    }

    /** Resolves the tenant's configured timezone string, falling back to UTC for a blank/invalid value. */
    public ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private Period toUtcPeriod(ZoneId zone, LocalDate startDate, LocalDate endDate) {
        ZonedDateTime startZoned = startDate.atStartOfDay(zone);
        ZonedDateTime endZoned = endDate.atStartOfDay(zone);
        LocalDateTime startUtc = startZoned.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endUtc = endZoned.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return new Period(startUtc, endUtc);
    }
}
