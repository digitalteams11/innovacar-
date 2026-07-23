package com.carrental.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Theme-default regression test: a brand-new user must default to LIGHT, not
 * the old "auto" (system) default — explicit theme selections must never be
 * silently overridden, and new accounts must not start on system-follow
 * behavior unless they explicitly choose it.
 */
class UserTest {

    @Test
    void onCreate_noThemeModeSet_defaultsToLight() {
        User user = User.builder().build();

        user.onCreate();

        assertThat(user.getThemeMode()).isEqualTo("light");
    }

    @Test
    void onCreate_explicitThemeModeSet_isPreserved() {
        User user = User.builder().themeMode("dark").build();

        user.onCreate();

        assertThat(user.getThemeMode()).isEqualTo("dark");
    }
}
