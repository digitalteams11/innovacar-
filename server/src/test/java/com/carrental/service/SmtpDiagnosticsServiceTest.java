package com.carrental.service;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpDiagnosticsServiceTest {

    private final SmtpDiagnosticsService service = new SmtpDiagnosticsService();

    @Test
    void missingConfigurationIsReportedWithoutAttemptingNetworkCalls() {
        var result = service.probe(null, 587, "user@example.com", "user@example.com", "secret", false);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SMTP_CONFIGURATION_MISSING");
        assertThat(result.configuration()).isEqualTo("FAILED");
        assertThat(result.dns()).isEqualTo("NOT_TESTED");
    }

    @Test
    void blankPasswordIsTreatedAsConfigurationMissing() {
        var result = service.probe("smtp.zoho.com", 587, "user@example.com", "user@example.com", "", false);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SMTP_CONFIGURATION_MISSING");
    }

    @Test
    void dnsFailureIsReportedWithoutAttemptingTcpOrAuth() {
        var result = service.probe("this-host-definitely-does-not-exist.invalid", 587,
                "user@example.com", "user@example.com", "secret", false);

        assertThat(result.success()).isFalse();
        assertThat(result.configuration()).isEqualTo("OK");
        assertThat(result.dns()).isEqualTo("FAILED");
        assertThat(result.tcp()).isEqualTo("NOT_TESTED");
        assertThat(result.tls()).isEqualTo("NOT_TESTED");
        assertThat(result.authentication()).isEqualTo("NOT_TESTED");
        assertThat(result.errorCode()).isEqualTo("SMTP_DNS_FAILED");
    }

    @Test
    void tcpConnectionRefusedIsNeverMisreportedAsAuthenticationFailure() throws Exception {
        // Bind a real socket then close it immediately — the OS will actively
        // refuse connections to that port on localhost right after, which is
        // exactly the "TCP failed before auth was ever attempted" scenario the
        // task requires to never be misclassified as an auth failure.
        int port;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            port = serverSocket.getLocalPort();
        }

        var result = service.probe("127.0.0.1", port, "user@example.com", "user@example.com", "secret", false);

        assertThat(result.success()).isFalse();
        assertThat(result.dns()).isEqualTo("OK");
        assertThat(result.tcp()).isEqualTo("FAILED");
        assertThat(result.tls()).isEqualTo("NOT_TESTED");
        assertThat(result.authentication()).isEqualTo("NOT_TESTED");
        assertThat(result.errorCode()).isIn("SMTP_CONNECTION_REFUSED", "SMTP_CONNECTION_TIMEOUT");
    }

    @Test
    void neverThrowsRegardlessOfInput() {
        // Garbage host, garbage port — must return a result, never propagate.
        var result = service.probe("::::not-a-host::::", -1, "x", "x", "x", false);
        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
    }
}
