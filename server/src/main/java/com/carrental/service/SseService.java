package com.carrental.service;

import com.carrental.entity.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseService {

    private final Map<Long, List<SseEmitter>> tenantEmitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SseEmitter subscribe(Long tenantId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout

        tenantEmitters.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(tenantId, emitter));
        emitter.onTimeout(() -> removeEmitter(tenantId, emitter));
        emitter.onError((e) -> removeEmitter(tenantId, emitter));

        // Send heartbeat to keep connection alive
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"type\":\"CONNECTED\"}"));
        } catch (IOException e) {
            log.warn("Failed to send SSE connect event to tenant {}", tenantId);
        }

        return emitter;
    }

    public void broadcastToTenant(Long tenantId, Notification notification) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters == null || emitters.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(notification);
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("notification")
                    .data(json);

            emitters.forEach(emitter -> {
                try {
                    emitter.send(event);
                } catch (IOException e) {
                    log.debug("Removing dead SSE emitter for tenant {}", tenantId);
                    removeEmitter(tenantId, emitter);
                }
            });
        } catch (Exception e) {
            log.error("Failed to broadcast notification", e);
        }
    }

    /**
     * Broadcast a typed contract event (separate from the notification bell) so
     * admin pages (ContractDetails, Contracts list) can auto-refresh in real time.
     * The event name is "contract_event" and the payload is a small JSON object.
     */
    public void broadcastContractEvent(Long tenantId, Long contractId,
                                       String contractNumber, String clientName,
                                       String status, String signatureStatus) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
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
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("contract_event")
                    .data(json);
            emitters.forEach(emitter -> {
                try {
                    emitter.send(event);
                } catch (IOException e) {
                    log.debug("Removing dead SSE emitter for tenant {} (contract_event)", tenantId);
                    removeEmitter(tenantId, emitter);
                }
            });
            log.debug("[SSE_CONTRACT_EVENT] tenantId={} contractId={} status={}", tenantId, contractId, status);
        } catch (Exception e) {
            log.error("Failed to broadcast contract_event for tenantId={} contractId={}", tenantId, contractId, e);
        }
    }

    public void sendHeartbeat() {
        tenantEmitters.forEach((tenantId, emitters) -> {
            emitters.forEach(emitter -> {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                } catch (IOException e) {
                    removeEmitter(tenantId, emitter);
                }
            });
        });
    }

    private void removeEmitter(Long tenantId, SseEmitter emitter) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                tenantEmitters.remove(tenantId);
            }
        }
    }
}
