package com.carrental;

import com.carrental.config.StartupPhaseLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

// com.carrental.legal.* is an in-progress, not-yet-wired compliance module whose
// entity.LegalAcceptance collides with the existing com.carrental.entity.LegalAcceptance
// (same JPA entity name, same table, different columns) and stops the whole app from
// booting. Excluded from scanning until that module is reconciled with the legacy entity.
@SpringBootApplication
@ComponentScan(basePackages = "com.carrental",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.carrental\\.legal\\..*"))
@EntityScan(basePackages = "com.carrental.entity")
@EnableJpaRepositories(basePackages = "com.carrental",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.carrental\\.legal\\.repository\\..*"))
@EnableScheduling
@Slf4j
public class LocationCarApplication {
    public static void main(String[] args) {
        // Safety net for any background thread this application doesn't manage
        // directly (Spring's own @Scheduled/@Async infrastructure already logs
        // and suppresses task exceptions on its own executors) — a raw thread
        // spawned by a third-party library must never silently die or, worse,
        // take the whole JVM down without a trace in the logs.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
                log.error("[UNCAUGHT_EXCEPTION] thread={} exceptionClass={} message={}",
                        thread.getName(), ex.getClass().getName(), ex.getMessage(), ex));

        // Only fires on a normal exit or a caught signal (SIGTERM) — a container
        // OOM-kill (SIGKILL) skips this entirely. Its absence in the logs right
        // before a restart is itself diagnostic: it means something outside the
        // JVM's control (e.g. the cgroup memory limit) ended the process, not the
        // JVM choosing to exit.
        long jvmStartNanos = System.nanoTime();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Runtime rt = Runtime.getRuntime();
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            log.warn("[SHUTDOWN_HOOK] uptimeMs={} heapUsedMb={} heapMaxMb={} threadCount={}",
                    (System.nanoTime() - jvmStartNanos) / 1_000_000,
                    (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
                    rt.maxMemory() / (1024 * 1024),
                    threadBean.getThreadCount());
        }, "shutdown-diagnostics"));

        SpringApplication app = new SpringApplication(LocationCarApplication.class);
        app.addListeners(new StartupPhaseLogger());
        app.run(args);
    }
}
