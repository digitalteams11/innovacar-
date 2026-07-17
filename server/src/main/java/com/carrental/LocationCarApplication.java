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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;

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
        // Logged before anything else — confirms whether -Xmx/-XX:MaxMetaspaceSize
        // (see server/railway.json) actually took effect, and shows the raw cgroup
        // limit the JVM saw (or didn't) independent of whatever heap flag is set.
        // A prior deployment reported heapMaxMb=127770 (~127GB) despite an explicit
        // -XX:MaxRAMPercentage flag — evidence the container's cgroup memory limit
        // either wasn't exposed to the JVM or wasn't detected, so the JVM fell back
        // to sizing against the host's physical RAM instead of the container's
        // actual allocation. Switching to absolute -Xmx/-Xms (which don't depend on
        // cgroup detection at all) is the fix; this log is what proves whether that
        // theory was right and whether the fix took.
        logContainerMemoryDiagnostics();

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

    /**
     * Logs the raw JVM-visible memory numbers plus the actual cgroup limit read
     * directly from the filesystem (cgroup v2 first, falling back to v1) —
     * independent of whatever -Xmx/-XX:MaxRAMPercentage flag is in effect, so
     * this always shows the ground truth of what the container actually allows.
     * Never throws: this must never be able to block or fail startup itself.
     */
    private static void logContainerMemoryDiagnostics() {
        try {
            Runtime rt = Runtime.getRuntime();
            long maxMb = rt.maxMemory() / (1024 * 1024);
            long totalMb = rt.totalMemory() / (1024 * 1024);
            long freeMb = rt.freeMemory() / (1024 * 1024);
            long usedMb = totalMb - freeMb;

            long metaspaceUsedMb = -1;
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                if ("Metaspace".equals(pool.getName())) {
                    metaspaceUsedMb = pool.getUsage().getUsed() / (1024 * 1024);
                    break;
                }
            }

            int availableProcessors = rt.availableProcessors();
            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
            String cgroupLimit = readCgroupMemoryLimit();

            log.info("[STARTUP_MEMORY] heapMaxMb={} heapTotalMb={} heapUsedMb={} metaspaceUsedMb={} "
                            + "availableProcessors={} threadCount={} cgroupMemoryLimit={}",
                    maxMb, totalMb, usedMb, metaspaceUsedMb, availableProcessors, threadCount, cgroupLimit);
        } catch (Exception ex) {
            log.warn("[STARTUP_MEMORY] Failed to collect memory diagnostics: {}", ex.getMessage());
        }
    }

    /** Reads the raw cgroup memory limit the JVM's own container-detection logic reads internally. */
    private static String readCgroupMemoryLimit() {
        // cgroup v2 (modern kernels/containers): a single unified file, "max" if unbounded.
        String v2 = readFirstLine("/sys/fs/cgroup/memory.max");
        if (v2 != null) return "v2:" + v2;
        // cgroup v1: a huge number (close to Long.MAX_VALUE) means "no limit set".
        String v1 = readFirstLine("/sys/fs/cgroup/memory/memory.limit_in_bytes");
        if (v1 != null) return "v1:" + v1;
        return "not-available (non-Linux host or no cgroup — expected in local dev)";
    }

    private static String readFirstLine(String path) {
        try {
            Path p = Path.of(path);
            if (!Files.isReadable(p)) return null;
            return Files.readString(p).trim();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
