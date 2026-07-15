package com.carrental.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs the resolved Railway PORT and the actual port Tomcat bound to, so a
 * proxy/target-port mismatch (Railway forwarding to a different port than the
 * one this container is listening on) shows up immediately in Deploy Logs
 * instead of manifesting only as an opaque 502 at the edge.
 */
@Slf4j
@Component
public class PortStartupDiagnostics implements ApplicationListener<WebServerInitializedEvent> {

    private final Environment environment;

    public PortStartupDiagnostics(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        String railwayPort = environment.getProperty("PORT", "(not set — using local default)");
        int boundPort = event.getWebServer().getPort();
        log.info("RAILWAY_PORT={}", railwayPort);
        log.info("SERVER_PORT={}", boundPort);
    }
}
