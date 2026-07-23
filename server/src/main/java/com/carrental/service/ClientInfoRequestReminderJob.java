package com.carrental.service;

import com.carrental.entity.ClientInfoRequestStatus;
import com.carrental.entity.ClientInformationRequest;
import com.carrental.repository.ClientInformationRequestRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled reminders/expiry sweep for the client self-fill workflow — each
 * reminder fires at most once per request (tracked via
 * reminder*SentAt columns) so a slow-to-respond client is never spammed.
 * Every row is processed independently (one failure never blocks the batch),
 * mirroring {@code ContractTrashCleanupJob}'s pattern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientInfoRequestReminderJob {

    private final ClientInformationRequestRepository requestRepository;
    private final ClientInformationRequestService requestService;

    /** Once a day: nudge clients who received the link 24h+ ago but never opened it. */
    @Scheduled(cron = "${app.client-info.reminder-not-opened.cron:0 15 8 * * *}")
    public void remindNotOpened() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<ClientInformationRequest> candidates =
                requestRepository.findAllByStatusAndOpenedAtIsNullAndReminderNotOpenedSentAtIsNullAndCreatedAtBefore(
                        ClientInfoRequestStatus.SENT, cutoff);
        for (ClientInformationRequest r : candidates) {
            try {
                TenantContext.setCurrentTenantId(r.getTenantId());
                requestService.resend(r.getId(), null);
                r.setReminderNotOpenedSentAt(LocalDateTime.now());
                requestRepository.save(r);
                log.info("[CLIENT_INFO] not-opened reminder sent id={} tenantId={}", r.getId(), r.getTenantId());
            } catch (Exception e) {
                log.warn("[CLIENT_INFO] not-opened reminder failed id={} reason={}", r.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Once a day: nudge clients who opened but never submitted, whose link expires within 24h. */
    @Scheduled(cron = "${app.client-info.reminder-expiry.cron:0 30 8 * * *}")
    public void remindExpiringSoon() {
        LocalDateTime cutoff = LocalDateTime.now().plusHours(24);
        List<ClientInformationRequest> candidates =
                requestRepository.findAllByStatusInAndReminderExpirySentAtIsNullAndExpiresAtBefore(
                        List.of(ClientInfoRequestStatus.SENT, ClientInfoRequestStatus.OPENED), cutoff);
        for (ClientInformationRequest r : candidates) {
            try {
                TenantContext.setCurrentTenantId(r.getTenantId());
                requestService.resend(r.getId(), null);
                r.setReminderExpirySentAt(LocalDateTime.now());
                requestRepository.save(r);
                log.info("[CLIENT_INFO] expiry reminder sent id={} tenantId={}", r.getId(), r.getTenantId());
            } catch (Exception e) {
                log.warn("[CLIENT_INFO] expiry reminder failed id={} reason={}", r.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Once an hour: mark past-due, never-submitted requests as EXPIRED (a lazy check already blocks their use — this makes it visible in the review queue too). */
    @Scheduled(cron = "${app.client-info.expiry-sweep.cron:0 5 * * * *}")
    public void expireStaleRequests() {
        List<ClientInformationRequest> expired = requestRepository.findAllByStatusInAndExpiresAtBefore(
                List.of(ClientInfoRequestStatus.SENT, ClientInfoRequestStatus.OPENED), LocalDateTime.now());
        for (ClientInformationRequest r : expired) {
            try {
                r.setStatus(ClientInfoRequestStatus.EXPIRED);
                requestRepository.save(r);
            } catch (Exception e) {
                log.warn("[CLIENT_INFO] expiry sweep failed id={} reason={}", r.getId(), e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("[CLIENT_INFO] expiry sweep marked {} request(s) EXPIRED", expired.size());
        }
    }
}
