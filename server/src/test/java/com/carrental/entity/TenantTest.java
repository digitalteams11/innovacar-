package com.carrental.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the free-trial calendar-month rule: exactly {@link Tenant#TRIAL_PERIOD_MONTHS}
 * calendar month(s) after the account's actual creation date, with safe end-of-month
 * handling (java.time.LocalDate#plusMonths already clamps to the last valid day of the
 * target month, so Jan 31 + 1 month lands on Feb 28/29, never rolls over into March).
 */
class TenantTest {

    @Test
    void trialIsExactlyOneCalendarMonth() {
        assertThat(Tenant.TRIAL_PERIOD_MONTHS).isEqualTo(1);
    }

    @Test
    void createdJuly18_trialEndsAugust18() {
        LocalDate created = LocalDate.of(2026, 7, 18);
        LocalDate trialEnd = created.plusMonths(Tenant.TRIAL_PERIOD_MONTHS);

        assertThat(trialEnd).isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    void createdJanuary31_trialEndsLastValidDayOfFebruary() {
        LocalDate created = LocalDate.of(2026, 1, 31);
        LocalDate trialEnd = created.plusMonths(Tenant.TRIAL_PERIOD_MONTHS);

        // 2026 is not a leap year -> Feb has 28 days.
        assertThat(trialEnd).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void createdJanuary31OnLeapYear_trialEndsFeb29() {
        LocalDate created = LocalDate.of(2028, 1, 31);
        LocalDate trialEnd = created.plusMonths(Tenant.TRIAL_PERIOD_MONTHS);

        assertThat(trialEnd).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void trialDaysRemaining_countsDownAndNeverGoesNegative() {
        Tenant tenant = Tenant.builder()
                .trialStartDate(LocalDate.now())
                .trialEndDate(LocalDate.now().plusDays(14))
                .build();
        assertThat(tenant.trialDaysRemaining()).isEqualTo(14);

        Tenant endingToday = Tenant.builder()
                .trialEndDate(LocalDate.now())
                .build();
        assertThat(endingToday.trialDaysRemaining()).isZero();

        Tenant alreadyPast = Tenant.builder()
                .trialEndDate(LocalDate.now().minusDays(5))
                .build();
        assertThat(alreadyPast.trialDaysRemaining()).isZero();
    }

    @Test
    void trialDaysRemaining_isZeroWithNoEndDate() {
        Tenant tenant = Tenant.builder().build();
        assertThat(tenant.trialDaysRemaining()).isZero();
        assertThat(tenant.isTrialExpired()).isFalse();
    }

    @Test
    void isTrialExpired_trueOnlyAfterEndDateHasPassed() {
        Tenant endingToday = Tenant.builder().trialEndDate(LocalDate.now()).build();
        assertThat(endingToday.isTrialExpired()).isFalse();

        Tenant endedYesterday = Tenant.builder().trialEndDate(LocalDate.now().minusDays(1)).build();
        assertThat(endedYesterday.isTrialExpired()).isTrue();

        Tenant endsTomorrow = Tenant.builder().trialEndDate(LocalDate.now().plusDays(1)).build();
        assertThat(endsTomorrow.isTrialExpired()).isFalse();
    }
}
