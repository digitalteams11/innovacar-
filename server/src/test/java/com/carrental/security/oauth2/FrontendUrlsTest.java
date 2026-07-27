package com.carrental.security.oauth2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against an OAuth2 redirect target ever pointing at www.innovacar.app
 * — Vercel permanently redirects that host to the non-www canonical one (see
 * frontend-web/vercel.json), which would otherwise insert an extra hop
 * between this redirect and the SPA actually loading and exchanging the
 * single-use code.
 */
class FrontendUrlsTest {

    @Test
    void stripsWwwFromHttpsHost() {
        assertThat(FrontendUrls.canonicalize("https://www.innovacar.app"))
                .isEqualTo("https://innovacar.app");
    }

    @Test
    void leavesNonWwwHostUnchanged() {
        assertThat(FrontendUrls.canonicalize("https://innovacar.app"))
                .isEqualTo("https://innovacar.app");
    }

    @Test
    void stripsTrailingSlashes() {
        assertThat(FrontendUrls.canonicalize("https://innovacar.app/"))
                .isEqualTo("https://innovacar.app");
        assertThat(FrontendUrls.canonicalize("https://www.innovacar.app///"))
                .isEqualTo("https://innovacar.app");
    }

    @Test
    void leavesLocalDevHostUnchanged() {
        assertThat(FrontendUrls.canonicalize("http://localhost:5173"))
                .isEqualTo("http://localhost:5173");
    }

    @Test
    void doesNotStripWwwFromUnrelatedSubdomain() {
        // A hypothetical "www-something.innovacar.app" must not be mangled —
        // only the literal www. prefix is stripped.
        assertThat(FrontendUrls.canonicalize("https://wwwstatic.innovacar.app"))
                .isEqualTo("https://wwwstatic.innovacar.app");
    }
}
