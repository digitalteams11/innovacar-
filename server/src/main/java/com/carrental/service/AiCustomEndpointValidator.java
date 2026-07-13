package com.carrental.service;

import com.carrental.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates a CUSTOM_OPENAI_COMPATIBLE {@code baseUrl} at save time to
 * reduce SSRF risk: rejects localhost/loopback/private-network addresses and
 * requires HTTPS, both bypassable only via {@code app.ai.dev-mode=true}
 * (never set in production).
 */
@Component
public class AiCustomEndpointValidator {

    @Value("${app.ai.dev-mode:false}")
    private boolean devMode;

    public void validate(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw AiServiceException.invalidCustomEndpoint("base URL is required for a custom provider.");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw AiServiceException.invalidCustomEndpoint("not a valid URL.");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!devMode && !"https".equalsIgnoreCase(scheme))) {
            throw AiServiceException.invalidCustomEndpoint("only HTTPS endpoints are allowed.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw AiServiceException.invalidCustomEndpoint("missing host.");
        }
        if (devMode) {
            return;
        }
        if (isLoopbackOrPrivateHostname(host)) {
            throw AiServiceException.invalidCustomEndpoint("localhost/loopback endpoints are not allowed.");
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isAnyLocalAddress()) {
                throw AiServiceException.invalidCustomEndpoint("private/loopback network addresses are not allowed.");
            }
        } catch (UnknownHostException ex) {
            throw AiServiceException.invalidCustomEndpoint("host could not be resolved.");
        }
    }

    private boolean isLoopbackOrPrivateHostname(String host) {
        String lower = host.toLowerCase();
        return lower.equals("localhost") || lower.equals("0.0.0.0") || lower.endsWith(".local");
    }
}
