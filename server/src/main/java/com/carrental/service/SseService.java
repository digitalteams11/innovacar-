package com.carrental.service;

import com.carrental.entity.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages SSE connections for real-time dashboard notifications/contract events.
 *
 * <p>At most one active emitter per (tenantId, userId): a reconnect from the
 * same user replaces (and safely completes) their previous emitter instead of
 * accumulating an unbounded number of connections. Combined with a bounded
 * emitter timeout (not {@code SseEmitter(0L)} / no-timeout) and an actual
 * scheduled heartbeat (previously dead code — {@code sendHeartbeat()} existed
 * but was never invoked from anywhere, so a half-open connection that never
 * triggered {@code onError} could sit in the registry indefinitely), this is
 * what actually reaps dead connections: every heartbeat tick, any emitter
 * whose underlying socket is gone throws on send and is removed right there.
 *
 * <p>Every write path here catches {@code Exception} broadly, not just
 * {@code IOException} — {@link SseEmitter#send} can also throw
 * {@code IllegalStateException} (e.g. a racing completion/timeout) and
 * {@code IllegalArgumentException}), and a stale-connection error must never
 * propagate out of a broadcast/heartbeat call into whatever business logic
 * or scheduler triggered it.
 */
@Slf4j
@Service
public class SseService {

    /** No proxy/load balancer keeps an idle HTTP connection open forever; re-establishing
     *  well before that is cheaper than debugging a silently-dead "permanent" connection. */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final Map<Long, Map<Long, SseEmitter>> tenantUserEmitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Safe, non-PII operational counters (Part 9) ─────────────────────────
    private final AtomicLong totalOpened = new AtomicLong();
    private final AtomicLong totalClosed = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();

    public record SseMetrics(long active, long totalOpened, long totalClosed, long totalFailed) {}

    public SseMetrics metrics() {
        long active = tenantUserEmitters.values().stream().mapToLong(Map::size).sum();
        return new SseMetrics(active, totalOpened.get(), totalClosed.get(), totalFailed.get());
    }

    /** Test seam — SseServiceTest overrides this to inject a mock emitter. */
    protected SseEmitter createEmitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MS);
    }

    /**
     * Subscribes user {@code userId} of tenant {@code tenantId}. If that user
     * already has an active emitter (e.g. a page refresh, a reconnect after a
     * network blip, or a duplicate tab), the old one is completed and replaced
     * — never left running alongside the new one.
     */
    public SseEmitter subscribe(Long tenantId, Long userId) {
        Map<Long, SseEmitter> userEmitters = tenantUserEmitters.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());

        SseEmitter previous = userEmitters.remove(userId);
        if (previous != null) {
            completeQuietly(previous);
        }

        SseEmitter emitter = createEmitter();
        userEmitters.put(userId, emitter);
        totalOpened.incrementAndGet();

        emitter.onCompletion(() -> { removeEmitter(tenantId, userId, emitter); totalClosed.incrementAndGet(); });
        emitter.onTimeout(() -> { removeEmitter(tenantId, userId, emitter); totalClosed.incrementAndGet(); });
        emitter.onError(ex -> { removeEmitter(tenantId, userId, emitter); totalFailed.incrementAndGet(); });

        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"type\":\"CONNECTED\"}"));
        } catch (Exception e) {
            // The connection died between creation and this first write — treat it the
            // same as any other failed send rather than leaving a dead emitter registered.
            log.debug("[SSE] initial connect event failed tenantId={} userId={}: {}", tenantId, userId, e.getMessage());
            removeEmitter(tenantId, userId, emitter);
            totalFailed.incrementAndGet();
        }

        return emitter;
    }

    public void broadcastToTenant(Long tenantId, Notification notification) {
        Map<Long, SseEmitter> emitters = tenantUserEmitters.get(tenantId);
        if (emitters == null || emitters.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(notification);
            SseEmitter.SseEventBuilder event = SseEmitter.event().name("notification").data(json);
            sendToAll(tenantId, emitters, event, "notification");
        } catch (Exception e) {
            log.error("Failed to serialize/broadcast notification for tenantId={}", tenantId, e);
        }
    }

    /**
     * Broadcast a typed contract event (separate from the notification bell) so
     * admin pages (ContractDetails, Contracts list) can auto-refresh in real time.
     */
    public void broadcastContractEvent(Long tenantId, Long contractId,
                                       String contractNumber, String clientName,
                                       String status, String signatureStatus) {
        Map<Long, SseEmitter> emitters = tenantUserEmitters.get(tenantId);
        if (emitters == null || emitters.isEmpty()) return;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "CONTRACT_SIGNED");
            payload.put("contractId", contractId);
            payload.put("contractNumber", contractNumber != null ? contractNumber : "");
            payload.put("clientName", clientName != null ? clientName : "");
            payload.put("status", status != null ? status : "SIGNED");
            payload.put("signatureStatus", signatureStatus != null ? signatureStatus : "CLIENT_SIGNED");
            String json = objectMapper.writeValueAsString(payload);
            SseEmitter.SseEventBuilder event = SseEmitter.event().name("contract_event").data(json);
            sendToAll(tenantId, emitters, event, "contract_event");
            log.debug("[SSE_CONTRACT_EVENT] tenantId={} contractId={} status={}", tenantId, contractId, status);
        } catch (Exception e) {
            log.error("Failed to serialize/broadcast contract_event for tenantId={} contractId={}", tenantId, contractId, e);
        }
    }

    /**
     * Real keep-alive + dead-connection reaper. Runs on Spring's shared scheduler
     * (see SchedulingConfig — one bounded pool, no per-client thread/executor).
     * Interval is well under typical reverse-proxy/load-balancer idle-connection
     * timeouts (usually 30-60s), and short enough that a half-open connection
     * (network drop with no clean FIN, laptop sleep, etc.) that never fires
     * {@code onError} on its own is still reaped within one tick instead of
     * lingering for the rest of the emitter's 30-minute timeout.
     */
    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeat() {
        tenantUserEmitters.forEach((tenantId, emitters) ->
                sendToAll(tenantId, emitters, SseEmitter.event().name("heartbeat").data("ping"), "heartbeat"));
    }

    private void sendToAll(Long tenantId, Map<Long, SseEmitter> emitters, SseEmitter.SseEventBuilder event, String eventName) {
        // Snapshot the keys first — sending can trigger onError/onCompletion
        // synchronously, which calls removeEmitter() and would otherwise mutate
        // this map while it's being iterated.
        emitters.keySet().stream().toList().forEach(userId -> {
            SseEmitter emitter = emitters.get(userId);
            if (emitter == null) return;
            try {
                emitter.send(event);
            } catch (Exception e) {
                // Expected, frequent, and not exceptional: a client closing a tab or losing
                // network is normal traffic, not an application error — one concise line,
                // never a full stack trace, per client disconnect.
                log.debug("[SSE] removing dead emitter tenantId={} userId={} event={} reason={}",
                        tenantId, userId, eventName, e.getClass().getSimpleName());
                removeEmitter(tenantId, userId, emitter);
                totalFailed.incrementAndGet();
            }
        });
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // Already completed/errored — nothing to do.
        }
    }

    private void removeEmitter(Long tenantId, Long userId, SseEmitter emitter) {
        Map<Long, SseEmitter> emitters = tenantUserEmitters.get(tenantId);
        if (emitters == null) return;
        // Only remove if it's still the same instance — a newer subscribe() may
        // already have replaced this user's entry with a fresh emitter.
        emitters.remove(userId, emitter);
        if (emitters.isEmpty()) {
            tenantUserEmitters.remove(tenantId, emitters);
        }
    }
}
