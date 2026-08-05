package com.carrental.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every transactional-email CTA in the app goes through this builder instead
 * of independently concatenating {@code frontendUrl + "/some/path"} — several
 * call sites used to forget the deployed app's required HashRouter {@code /#/}
 * prefix, producing a button that looked fine in the email but opened the
 * bare app shell (resolving to "/") instead of the intended route once
 * clicked. These assert every method emits an absolute https:// URL with
 * exactly one {@code /#/}, no malformed double slashes, and parameters
 * encoded exactly once.
 */
class EmailActionUrlBuilderTest {

    private final EmailActionUrlBuilder builder = new EmailActionUrlBuilder("https://innovacar.app");

    @Test
    void dashboardUrl_isAbsoluteHttpsWithHashRoute() {
        assertThat(builder.dashboardUrl()).isEqualTo("https://innovacar.app/#/");
    }

    @Test
    void reportArchiveUrl_withId_carriesReportIdAsQueryParam() {
        assertThat(builder.reportArchiveUrl(500L)).isEqualTo("https://innovacar.app/#/report-archive?reportId=500");
    }

    @Test
    void reportArchiveUrl_withoutId_isJustTheArchiveRoute() {
        assertThat(builder.reportArchiveUrl(null)).isEqualTo("https://innovacar.app/#/report-archive");
    }

    @Test
    void contractUrl_encodesThePathSegment() {
        assertThat(builder.contractUrl(42L)).isEqualTo("https://innovacar.app/#/contracts/42");
    }

    @Test
    void passwordResetUrl_encodesTheTokenExactlyOnce() {
        // A token containing '+' and '/' (base64url-adjacent chars) must not be
        // double-encoded (spec: "do not URL-encode the entire final URL twice").
        String url = builder.passwordResetUrl("abc+123/xyz==");
        assertThat(url).isEqualTo("https://innovacar.app/#/reset-password?token=abc%2B123%2Fxyz%3D%3D");
        assertThat(url).doesNotContain("%2525"); // would appear if '%' itself got re-encoded (double-encoding tell)
    }

    @Test
    void emailVerificationUrl_containsTheRawTokenEncodedOnce() {
        assertThat(builder.emailVerificationUrl("raw-token-123"))
                .isEqualTo("https://innovacar.app/#/verify-email?token=raw-token-123");
    }

    @Test
    void additionalDriverSignatureUrl_isAPublicRouteRequiringNoLogin() {
        // "requires no login" is enforced by the frontend route table (App.tsx) and
        // PublicAdditionalDriverController — this only asserts the URL shape itself.
        assertThat(builder.additionalDriverSignatureUrl("tok"))
                .isEqualTo("https://innovacar.app/#/sign/additional-driver/tok");
    }

    @Test
    void contractSignatureUrl_encodesBothContractIdAndToken() {
        assertThat(builder.contractSignatureUrl(7L, "raw tok/en"))
                .isEqualTo("https://innovacar.app/#/contract-sign/7/raw%20tok%2Fen");
    }

    @Test
    void neverProducesADoubleSlashAfterTheHash_evenWithATrailingSlashInFrontendUrl() {
        EmailActionUrlBuilder trailingSlashBuilder = new EmailActionUrlBuilder("https://innovacar.app///");
        assertThat(trailingSlashBuilder.dashboardUrl()).isEqualTo("https://innovacar.app/#/");
        assertThat(trailingSlashBuilder.reportArchiveUrl()).doesNotContain("//#/").doesNotContain("app//");
    }

    @Test
    void neverResolvesToLocalhostInThisTestConfiguration() {
        // Guards the specific class of bug this task fixed — a value that silently
        // fell back to localhost/dev would fail this outright.
        assertThat(builder.dashboardUrl()).doesNotContain("localhost");
        assertThat(builder.reportArchiveUrl()).doesNotContain("localhost");
        assertThat(builder.dashboardUrl()).startsWith("https://");
    }

    @Test
    void withLoginReturnTo_wrapsAnAlreadyBuiltDestinationBehindLoginWithoutDoubleEncoding() {
        String dest = builder.reportArchiveUrl(500L);
        String wrapped = builder.withLoginReturnTo(dest);
        assertThat(wrapped).isEqualTo("https://innovacar.app/#/login?returnTo=%2Freport-archive%3FreportId%3D500");
        // Same-origin only: the encoded returnTo value is a relative hash-path this
        // class itself built, never an absolute/external URL an attacker could smuggle
        // in — asserted by there being no *second* "://" beyond the leading https://.
        assertThat(wrapped.substring("https://".length())).doesNotContain("://");
    }
}
