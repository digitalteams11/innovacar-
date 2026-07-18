package com.carrental.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * One small, bounded executor for outbound transactional email sends (password
 * reset codes, etc.) that must never run on a request thread holding a
 * database transaction/connection.
 *
 * <p>{@code HttpEmailProvider} has its own 10-second HTTP timeout to ZeptoMail.
 * Calling it synchronously from inside an {@code @Transactional} method holds
 * that method's checked-out Hikari connection for the whole call — with a
 * small pool (5 connections in production), a handful of slow/degraded email
 * sends is enough to exhaust the pool and make every other endpoint that also
 * needs a connection (login, public branding) queue and fail with Hikari's own
 * connection-timeout, surfacing as a 503 on completely unrelated requests.
 *
 * <p>Deliberately bounded (not {@code SimpleAsyncTaskExecutor}, which spawns an
 * unbounded thread per task) — a queue capacity gives a hard ceiling, and a
 * full queue rejects immediately (caller logs a warning and moves on) rather
 * than piling up unbounded background work.
 */
@Configuration
public class EmailDispatchConfig {

    @Bean("emailDispatchExecutor")
    public Executor emailDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-dispatch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
