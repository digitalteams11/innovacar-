package com.carrental.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopReleaseValidatorTest {

    private final DesktopReleaseValidator validator = new DesktopReleaseValidator(
            "github.com,objects.githubusercontent.com,r2.cloudflarestorage.com,api.innovacar.app");

    @Test
    void validSemver_accepted() {
        assertThat(validator.isValidSemanticVersion("1.2.0")).isTrue();
        assertThat(validator.isValidSemanticVersion("2.0.0-beta.1")).isTrue();
    }

    @Test
    void invalidSemver_rejected() {
        assertThat(validator.isValidSemanticVersion("1.2")).isFalse();
        assertThat(validator.isValidSemanticVersion("v1.2.0")).isFalse();
        assertThat(validator.isValidSemanticVersion("not-a-version")).isFalse();
        assertThat(validator.isValidSemanticVersion(null)).isFalse();
    }

    @Test
    void httpsUrlOnAllowedDomain_accepted() {
        assertThat(validator.isAllowedDownloadUrl("https://github.com/innovacar/desktop/releases/download/v1.2.0/Innovacar-Setup-1.2.0.exe")).isTrue();
        assertThat(validator.isAllowedDownloadUrl("https://releases.r2.cloudflarestorage.com/setup.exe")).isTrue();
    }

    @Test
    void nonHttpsScheme_rejected() {
        assertThat(validator.isAllowedDownloadUrl("http://github.com/setup.exe")).isFalse();
        assertThat(validator.isAllowedDownloadUrl("javascript:alert(1)")).isFalse();
        assertThat(validator.isAllowedDownloadUrl("file:///C:/setup.exe")).isFalse();
    }

    @Test
    void disallowedDomain_rejected() {
        assertThat(validator.isAllowedDownloadUrl("https://evil-attacker.com/setup.exe")).isFalse();
        assertThat(validator.isAllowedDownloadUrl("https://github.com.evil-attacker.com/setup.exe")).isFalse();
    }

    @Test
    void blankOrMalformedUrl_rejected() {
        assertThat(validator.isAllowedDownloadUrl("")).isFalse();
        assertThat(validator.isAllowedDownloadUrl(null)).isFalse();
        assertThat(validator.isAllowedDownloadUrl("not a url")).isFalse();
    }

    @Test
    void sha256_validatesLengthAndHex() {
        assertThat(validator.isValidSha256(null)).isTrue();
        assertThat(validator.isValidSha256("")).isTrue();
        assertThat(validator.isValidSha256("a".repeat(64))).isTrue();
        assertThat(validator.isValidSha256("a".repeat(63))).isFalse();
        assertThat(validator.isValidSha256("z".repeat(64))).isFalse();
    }
}
