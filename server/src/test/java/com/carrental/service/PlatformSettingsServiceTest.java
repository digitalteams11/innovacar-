package com.carrental.service;

import com.carrental.entity.PlatformSettings;
import com.carrental.repository.PlatformSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the platform_settings singleton-safety fix: the app used to read/write this
 * "singleton" table via an unordered findAll().stream().findFirst() plus independent
 * lazy row-creation at several call sites, which could create more than one row under
 * concurrent first-load requests — after which SMTP save/read/test/diagnose endpoints
 * could silently bind to different rows (symptom: "SMTP host not set" right after saving).
 */
class PlatformSettingsServiceTest {

    private PlatformSettingsRepository repository;
    private AuditLogService auditLogService;
    private PlatformSettingsService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(PlatformSettingsRepository.class);
        auditLogService = Mockito.mock(AuditLogService.class);
        service = new PlatformSettingsService(repository, auditLogService);
    }

    @Test
    void returnsExistingRowWithoutCreatingANewOne() {
        PlatformSettings existing = PlatformSettings.builder().id(7L).smtpHost("smtp.zoho.com").build();
        when(repository.findTopByOrderByIdAsc()).thenReturn(Optional.of(existing));

        PlatformSettings result = service.getOrCreateSingleton();

        assertSame(existing, result);
        verify(repository, never()).save(any());
    }

    @Test
    void createsExactlyOneRowWhenTableIsEmpty() {
        when(repository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());
        PlatformSettings created = PlatformSettings.builder().id(1L).build();
        when(repository.save(any())).thenReturn(created);

        PlatformSettings result = service.getOrCreateSingleton();

        assertSame(created, result);
        verify(repository, times(1)).save(any());
    }

    /**
     * Simulates the real-world trigger: the Email Center page firing several settings
     * requests in parallel (Promise.all) on first load against an empty table. Without
     * the `synchronized` guard on getOrCreateSingleton(), concurrent threads could each
     * observe an empty table and each insert their own row.
     */
    @Test
    @Timeout(10)
    void concurrentFirstLoadCreatesOnlyOneRowNotSeveral() throws InterruptedException {
        AtomicInteger rowCount = new AtomicInteger(0);
        AtomicInteger nextId = new AtomicInteger(1);
        // Mock repository state: empty until the first save(), then always returns that row.
        final PlatformSettings[] singleton = new PlatformSettings[1];
        when(repository.findTopByOrderByIdAsc()).thenAnswer(inv ->
                singleton[0] == null ? Optional.empty() : Optional.of(singleton[0]));
        when(repository.save(any())).thenAnswer(inv -> {
            rowCount.incrementAndGet();
            PlatformSettings saved = PlatformSettings.builder().id((long) nextId.getAndIncrement()).build();
            singleton[0] = saved;
            return saved;
        });

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        service.getOrCreateSingleton();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            ready.await();
            go.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(1, rowCount.get(), "Concurrent first-load calls must create exactly one platform_settings row");
    }
}
