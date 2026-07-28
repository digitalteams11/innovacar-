package com.carrental.controller;

import com.carrental.entity.ContactRequest;
import com.carrental.repository.ContactRequestRepository;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SupportRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Public, unauthenticated entry point for the "Contact Us" module. This is
 * deliberately independent of the Support Ticket system: a contact request
 * is saved as its own {@link ContactRequest} record and only ever becomes a
 * {@link com.carrental.entity.SupportTicket} via an explicit Super Admin
 * "convert to ticket" action (see SuperAdminContactRequestController).
 *
 * <p>The database save is the source of truth. Notification emails run on
 * the bounded {@code emailDispatchExecutor} (see EmailDispatchConfig and
 * AuthService.dispatchResetCodeEmail for the same pattern) — never
 * synchronously on this request thread. ZeptoMail (or a slow/hung DNS
 * resolution to it, which HttpEmailProvider's own HttpClient timeout does
 * NOT bound) previously ran inline inside this method's transaction, holding
 * a Hikari connection open for however long two sequential external HTTP
 * calls took; a degraded provider turned every contact submission into a
 * request that hung well past the frontend's own timeout, surfacing there as
 * "API server unavailable" even though the request had already been saved.
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicContactController {

    private final ContactRequestRepository contactRequestRepository;
    private final SupportRoutingService supportRoutingService;
    private final PlatformEmailService platformEmailService;

    @Qualifier("emailDispatchExecutor")
    private final Executor emailDispatchExecutor;

    @PostMapping("/contact")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitContactForm(@RequestBody Map<String, Object> requestBody) {
        String subject = required(requestBody, "subject");
        String message = required(requestBody, "message");
        String requesterEmail = required(requestBody, "requesterEmail");
        String requesterName = text(requestBody.get("requesterName"), "Anonymous");
        String requesterPhone = text(requestBody.get("requesterPhone"), null);
        String category = text(requestBody.get("category"), "GENERAL").toUpperCase(Locale.ROOT);

        if (subject.length() < 5 || subject.length() > 150) {
            log.info("[CONTACT_SUBMIT_REJECTED] field=subject reason=length");
            throw new IllegalArgumentException("Subject must be between 5 and 150 characters");
        }
        if (message.length() < 10 || message.length() > 5000) {
            log.info("[CONTACT_SUBMIT_REJECTED] field=message reason=length");
            throw new IllegalArgumentException("Message must be between 10 and 5000 characters");
        }
        if (!requesterEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            log.info("[CONTACT_SUBMIT_REJECTED] field=requesterEmail reason=invalid_format");
            throw new IllegalArgumentException("A valid requester email is required");
        }

        String destinationEmail = supportRoutingService.resolveDestinationEmail(
                SupportRoutingService.CHANNEL_CONTACT, category);

        log.info("[CONTACT_SUBMIT_BEGIN] category={}", category);

        ContactRequest request = contactRequestRepository.save(ContactRequest.builder()
                .subject(subject)
                .message(message)
                .category(category)
                .requesterName(requesterName)
                .requesterEmail(requesterEmail)
                .requesterPhone(requesterPhone)
                .destinationEmail(destinationEmail)
                .status("NEW")
                .build());
        log.info("[CONTACT_SAVED] requestNumber={} category={}", request.getRequestNumber(), category);

        // The save above is the source of truth and is already committed by the
        // time the response below is written. Notification emails are dispatched
        // on a bounded background executor — never on this request thread — so a
        // slow or unreachable email provider can only ever delay a notification,
        // never the HTTP response to the visitor (see class Javadoc).
        dispatchContactEmails(request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "We sent your request to the right team",
                "requestNumber", request.getRequestNumber()
        ));
    }

    private void dispatchContactEmails(ContactRequest request) {
        try {
            emailDispatchExecutor.execute(() -> {
                try {
                    platformEmailService.sendContactRequestCreatedInternal(request);
                    log.info("[CONTACT_EMAIL_SENT] requestNumber={} target=internal", request.getRequestNumber());
                } catch (Exception ex) {
                    log.warn("[CONTACT_EMAIL_FAILED] requestNumber={} target=internal exceptionClass={} message={}",
                            request.getRequestNumber(), ex.getClass().getName(), ex.getMessage());
                }
                try {
                    platformEmailService.sendContactRequestConfirmation(request);
                    log.info("[CONTACT_EMAIL_SENT] requestNumber={} target=requester", request.getRequestNumber());
                } catch (Exception ex) {
                    log.warn("[CONTACT_EMAIL_FAILED] requestNumber={} target=requester exceptionClass={} message={}",
                            request.getRequestNumber(), ex.getClass().getName(), ex.getMessage());
                }
            });
        } catch (RejectedExecutionException rex) {
            // Executor saturated — the request is already saved and visible to Super
            // Admin regardless; never block or fail the visitor's response over this.
            log.warn("[CONTACT_EMAIL_FAILED] requestNumber={} reason=dispatch_queue_full", request.getRequestNumber());
        }
    }

    private String required(Map<String, Object> request, String key) {
        String value = text(request.get(key), "").trim();
        if (value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
