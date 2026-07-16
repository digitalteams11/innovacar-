package com.carrental.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs a safe (no secrets) snapshot of process state whenever the Spring
 * context starts closing — whether that's a graceful SIGTERM (Railway
 * redeploy/scale-down) or the JVM tearing down after an unhandled fatal
 * error. This is the one piece of evidence that survives a crash where the
 * actual exception never made it into the deploy log (e.g. the process was
 * killed by the container's OOM killer rather than throwing a Java
 * exception) — memory/thread state right before shutdown at least narrows
 * down whether resource exhaustion was involved.
 */
@Slf4j
@Component
public class ShutdownDiagnostics implements ApplicationListener<ContextClosedEvent> {

    private final Environment environment;

    public ShutdownDiagnostics(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        int threadCount = Thread.activeCount();
        String[] profiles = environment.getActiveProfiles();

        log.warn("[SHUTDOWN_DIAGNOSTICS] Spring context closing — activeProfiles={} "
                        + "heapUsedMb={} heapMaxMb={} activeThreadCount={}",
                profiles.length == 0 ? "default" : String.join(",", profiles),
                usedMb, maxMb, threadCount);
    }
}
