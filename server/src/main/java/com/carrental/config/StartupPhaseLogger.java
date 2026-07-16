package com.carrental.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Registered directly on {@code SpringApplication} (not as a {@code @Component})
 * so it also receives the two lifecycle events that fire before any
 * {@code ApplicationContext} exists — {@link ApplicationStartingEvent} and
 * {@link org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent}.
 * A regular bean can never see those, which makes it impossible to prove from
 * bean-level logs alone whether a hang happened before or during context
 * refresh. This logs every phase transition with elapsed time and heap/thread
 * counts (never secrets), so a silent restart-loop leaves a clear "last phase
 * reached" trail even when the JVM is killed with no chance to log a stack
 * trace (e.g. a container OOM-kill via SIGKILL).
 */
@Slf4j
public class StartupPhaseLogger implements ApplicationListener<ApplicationEvent> {

    private final long jvmStartNanos = System.nanoTime();

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationStartingEvent
                || event instanceof org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
                || event instanceof org.springframework.boot.context.event.ApplicationContextInitializedEvent
                || event instanceof org.springframework.boot.context.event.ApplicationPreparedEvent
                || event instanceof ContextRefreshedEvent
                || event instanceof ApplicationStartedEvent
                || event instanceof ApplicationReadyEvent) {
            logPhase(event.getClass().getSimpleName());
        } else if (event instanceof ApplicationFailedEvent failed) {
            logPhase("ApplicationFailedEvent");
            Throwable ex = failed.getException();
            log.error("[STARTUP_FAILED] phase=ApplicationFailedEvent exceptionClass={} message={}",
                    ex.getClass().getName(), ex.getMessage(), ex);
        }
        // ContextClosedEvent is already covered by ShutdownDiagnostics (a regular
        // @Component bean) — no need to duplicate it here.
    }

    private void logPhase(String phaseName) {
        Runtime rt = Runtime.getRuntime();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        log.info("[STARTUP_PHASE] {} elapsedMs={} heapUsedMb={} heapMaxMb={} threadCount={}",
                phaseName,
                elapsedMs(),
                (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
                rt.maxMemory() / (1024 * 1024),
                threadBean.getThreadCount());
    }

    private long elapsedMs() {
        return (System.nanoTime() - jvmStartNanos) / 1_000_000;
    }
}
