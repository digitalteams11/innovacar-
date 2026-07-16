package com.carrental.controller;

import com.carrental.service.SseService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Safe, non-secret operational metrics for the Super Admin diagnostics page
 * (Part 9): SSE connection counts, JVM heap, Hikari pool state, Tomcat
 * thread-pool state. Nothing here is a substitute for real APM — it exists
 * so a runtime problem (a growing SSE count, an exhausted Hikari pool, a
 * saturated Tomcat thread pool) is visible from the app itself, without
 * needing Railway dashboard access.
 *
 * <p>Deliberately excludes anything that could be a secret or personal data:
 * no connection strings, no usernames, no request bodies, no user identifiers
 * beyond aggregate counts.
 */
@RestController
@RequestMapping("/api/super-admin/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SystemDiagnosticsController {

    private final SseService sseService;
    private final DataSource dataSource;

    @GetMapping("/runtime-metrics")
    public Map<String, Object> runtimeMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sse", sseMetrics());
        result.put("jvmHeap", heapMetrics());
        result.put("hikari", hikariMetrics());
        result.put("tomcatThreadPool", tomcatThreadPoolMetrics());
        return result;
    }

    private Map<String, Object> sseMetrics() {
        SseService.SseMetrics m = sseService.metrics();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("active", m.active());
        map.put("totalOpened", m.totalOpened());
        map.put("totalClosed", m.totalClosed());
        map.put("totalFailed", m.totalFailed());
        return map;
    }

    private Map<String, Object> heapMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("usedMb", usedMb);
        map.put("maxMb", maxMb);
        map.put("activeThreadCount", Thread.activeCount());
        return map;
    }

    private Map<String, Object> hikariMetrics() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            if (pool != null) {
                map.put("activeConnections", pool.getActiveConnections());
                map.put("idleConnections", pool.getIdleConnections());
                map.put("totalConnections", pool.getTotalConnections());
                map.put("threadsAwaitingConnection", pool.getThreadsAwaitingConnection());
            }
            map.put("maxPoolSize", hikariDataSource.getMaximumPoolSize());
        } else {
            map.put("available", false);
        }
        return map;
    }

    /** Reads Tomcat's own thread-pool MBean rather than the JVM-wide thread count. */
    private Map<String, Object> tomcatThreadPoolMetrics() {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> names = server.queryNames(new ObjectName("Tomcat:type=ThreadPool,*"), null);
            if (names.isEmpty()) {
                map.put("available", false);
                return map;
            }
            ObjectName name = names.iterator().next();
            map.put("currentThreadsBusy", server.getAttribute(name, "currentThreadsBusy"));
            map.put("maxThreads", server.getAttribute(name, "maxThreads"));
            map.put("currentThreadCount", server.getAttribute(name, "currentThreadCount"));
        } catch (Exception e) {
            map.put("available", false);
        }
        return map;
    }
}
