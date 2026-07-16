package com.carrental.service;

import com.carrental.entity.Notification;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies SseService's connection-registry lifecycle using Mockito-mocked
 * {@link SseEmitter} instances (injected via the {@code createEmitter()} test
 * seam) — no real Tomcat/async servlet machinery needed. The registered
 * onCompletion/onTimeout/onError callbacks are captured and invoked directly,
 * exactly as Spring MVC's async request processing would invoke them for a
 * real client.
 */
class SseServiceTest {

    /** Emitters to hand out on successive subscribe() calls, in order. */
    private final Deque<SseEmitter> queuedEmitters = new ArrayDeque<>();

    private final SseService service = new SseService() {
        @Override
        protected SseEmitter createEmitter() {
            SseEmitter next = queuedEmitters.poll();
            return next != null ? next : mock(SseEmitter.class);
        }
    };

    private SseEmitter nextMockEmitter() {
        SseEmitter emitter = mock(SseEmitter.class);
        queuedEmitters.add(emitter);
        return emitter;
    }

    @SuppressWarnings("unchecked")
    private Runnable capturedOnCompletion(SseEmitter emitter) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Runnable capturedOnTimeout(SseEmitter emitter) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Consumer<Throwable> capturedOnError(SseEmitter emitter) {
        ArgumentCaptor<Consumer<Throwable>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(captor.capture());
        return captor.getValue();
    }

    @Test
    void subscribeRegistersOneActiveEmitter() {
        nextMockEmitter();
        service.subscribe(1L, 100L);

        assertThat(service.metrics().active()).isEqualTo(1);
        assertThat(service.metrics().totalOpened()).isEqualTo(1);
    }

    @Test
    void completionRemovesTheEmitter() {
        SseEmitter emitter = nextMockEmitter();
        service.subscribe(1L, 100L);

        capturedOnCompletion(emitter).run();

        assertThat(service.metrics().active()).isZero();
        assertThat(service.metrics().totalClosed()).isEqualTo(1);
    }

    @Test
    void timeoutRemovesTheEmitter() {
        SseEmitter emitter = nextMockEmitter();
        service.subscribe(1L, 100L);

        capturedOnTimeout(emitter).run();

        assertThat(service.metrics().active()).isZero();
        assertThat(service.metrics().totalClosed()).isEqualTo(1);
    }

    @Test
    void errorRemovesTheEmitter() {
        SseEmitter emitter = nextMockEmitter();
        service.subscribe(1L, 100L);

        capturedOnError(emitter).accept(new IOException("connection reset"));

        assertThat(service.metrics().active()).isZero();
        assertThat(service.metrics().totalFailed()).isEqualTo(1);
    }

    @Test
    void duplicateSubscribeFromSameUserReplacesOldEmitterInsteadOfAccumulating() {
        SseEmitter first = nextMockEmitter();
        service.subscribe(1L, 100L);
        assertThat(service.metrics().active()).isEqualTo(1);

        nextMockEmitter();
        service.subscribe(1L, 100L);

        // Still exactly one active connection for this user — the old one was
        // completed and replaced, not left running alongside the new one (Part 3).
        assertThat(service.metrics().active()).isEqualTo(1);
        verify(first).complete();
    }

    @Test
    void differentUsersInSameTenantEachGetTheirOwnEmitter() {
        nextMockEmitter();
        nextMockEmitter();
        service.subscribe(1L, 100L);
        service.subscribe(1L, 200L);

        assertThat(service.metrics().active()).isEqualTo(2);
    }

    @Test
    void failedSendDuringBroadcastRemovesEmitterAndDoesNotThrow() throws IOException {
        SseEmitter emitter = nextMockEmitter();
        service.subscribe(1L, 100L);
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        Notification notification = Notification.builder()
                .tenantId(1L).title("t").message("m").read(false).build();

        assertThatCode(() -> service.broadcastToTenant(1L, notification)).doesNotThrowAnyException();

        assertThat(service.metrics().active()).isZero();
        assertThat(service.metrics().totalFailed()).isEqualTo(1);
    }

    @Test
    void illegalStateExceptionDuringSendIsCaughtNotJustIOException() throws IOException {
        // SseEmitter#send can throw IllegalStateException (e.g. a racing
        // completion), not only IOException — the broadcast path must catch both.
        SseEmitter emitter = nextMockEmitter();
        service.subscribe(1L, 100L);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        Notification notification = Notification.builder()
                .tenantId(1L).title("t").message("m").read(false).build();

        assertThatCode(() -> service.broadcastToTenant(1L, notification)).doesNotThrowAnyException();
        assertThat(service.metrics().active()).isZero();
    }

    @Test
    void heartbeatRemovesDeadEmittersWithoutThrowing() throws IOException {
        SseEmitter alive = nextMockEmitter();
        service.subscribe(1L, 100L);
        SseEmitter dead = nextMockEmitter();
        service.subscribe(1L, 200L);
        doThrow(new IOException("broken pipe")).when(dead).send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(service::sendHeartbeat).doesNotThrowAnyException();

        assertThat(service.metrics().active()).isEqualTo(1);
        verify(alive, Mockito.never()).complete();
    }

    @Test
    void broadcastToTenantWithNoSubscribersIsANoOp() {
        Notification notification = Notification.builder()
                .tenantId(999L).title("t").message("m").read(false).build();
        assertThatCode(() -> service.broadcastToTenant(999L, notification)).doesNotThrowAnyException();
    }

    @Test
    void broadcastContractEventDoesNotThrowWhenSendFails() throws IOException {
        SseEmitter emitter = nextMockEmitter();
        service.subscribe(1L, 100L);
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(() -> service.broadcastContractEvent(1L, 5L, "CTR-1", "Client", "SIGNED", "CLIENT_SIGNED"))
                .doesNotThrowAnyException();

        assertThat(service.metrics().active()).isZero();
    }
}
