package com.carrental.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure validation rules for publishing a {@code DesktopRelease} — kept out of
 * the controller so semver/URL-scheme rules are unit-testable without a
 * Spring context. Rejects anything that isn't a strict MAJOR.MINOR.PATCH
 * version or an HTTPS URL on an approved release-hosting domain, so Super
 * Admin can never accidentally (or maliciously) publish a
 * {@code javascript:}/{@code file:} URI or an arbitrary untrusted host.
 */
@Component
public class DesktopReleaseValidator {

    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$");

    private final List<String> allowedDownloadHosts;

    public DesktopReleaseValidator(
            @Value("${app.desktop.allowed-download-hosts:github.com,objects.githubusercontent.com,release-assets.githubusercontent.com,r2.cloudflarestorage.com,s3.amazonaws.com,api.innovacar.app}")
            String allowedDownloadHosts) {
        this.allowedDownloadHosts = Arrays.stream(allowedDownloadHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isBlank())
                .toList();
    }

    public boolean isValidSemanticVersion(String version) {
        return version != null && SEMVER.matcher(version).matches();
    }

    /** True only for an HTTPS URL whose host is exactly (or a subdomain of) an approved release host. */
    public boolean isAllowedDownloadUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            String lowerHost = host.toLowerCase(java.util.Locale.ROOT);
            return allowedDownloadHosts.stream().anyMatch(allowed ->
                    lowerHost.equals(allowed) || lowerHost.endsWith("." + allowed));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public boolean isValidSha256(String sha256) {
        return sha256 == null || sha256.isBlank() || sha256.matches("^[a-fA-F0-9]{64}$");
    }
}
