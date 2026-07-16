package com.carrental.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables Spring's scheduled task execution with one bounded, shared
 * scheduler pool.
 *
 * <p>Without an explicit {@link ThreadPoolTaskScheduler} bean, Spring Boot
 * defaults to a single-threaded scheduler shared by every {@code @Scheduled}
 * method in the app. This app has jobs that can legitimately run for
 * minutes (e.g. {@code BackupService}'s pg_dump/pg_restore, up to a 30/60
 * minute timeout) sharing that one thread with jobs that need to run
 * reliably every few seconds (e.g. {@code SseService.sendHeartbeat}, every
 * 25s — the only thing that reaps dead SSE connections). On a single-thread
 * pool, one long backup silently starves every other scheduled job for its
 * entire duration. A small bounded pool (not unlimited/cached — still a
 * fixed ceiling) lets scheduled jobs run concurrently without either
 * problem.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setErrorHandler(ex -> org.slf4j.LoggerFactory.getLogger(SchedulingConfig.class)
                .error("[SCHEDULED_TASK_ERROR] Uncaught exception in a scheduled task — the task itself should "
                        + "have handled this; the scheduler pool continues running other jobs regardless.", ex));
        return scheduler;
    }
}
