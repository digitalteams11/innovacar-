package com.carrental.controller;

import com.carrental.entity.ContactRequest;
import com.carrental.repository.ContactRequestRepository;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SupportRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

/**
 * Public, unauthenticated entry point for the "Contact Us" module. This is
 * deliberately independent of the Support Ticket system: a contact request
 * is saved as its own {@link ContactRequest} record and only ever becomes a
 * {@link com.carrental.entity.SupportTicket} via an explicit Super Admin
 * "convert to ticket" action (see SuperAdminContactRequestController).
 *
 * <p>The database save is the source of truth. Notification emails are
 * attempted afterward and their failure is caught here so it can never roll
 * back an already-saved request — a ZeptoMail outage must not make contact
 * submissions disappear.
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicContactController {

    private final ContactRequestRepository contactRequestRepository;
    private final SupportRoutingService supportRoutingService;
    private final PlatformEmailService platformEmailService;

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
            throw new IllegalArgumentException("Subject must be between 5 and 150 characters");
        }
        if (message.length() < 10 || message.length() > 5000) {
            throw new IllegalArgumentException("Message must be between 10 and 5000 characters");
        }
        if (!requesterEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("A valid requester email is required");
        }

        String destinationEmail = supportRoutingService.resolveDestinationEmail(
                SupportRoutingService.CHANNEL_CONTACT, category);

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

        // The save above is committed; email delivery is best-effort and must never
        // cause the already-saved request to be rolled back if it throws.
        boolean emailFailed = false;
        try {
            platformEmailService.sendContactRequestCreatedInternal(request);
        } catch (Exception ex) {
            log.warn("[CONTACT] Failed to notify internal team for {}: {}", request.getRequestNumber(), ex.getMessage());
            emailFailed = true;
        }
        try {
            platformEmailService.sendContactRequestConfirmation(request);
        } catch (Exception ex) {
            log.warn("[CONTACT] Failed to send confirmation for {}: {}", request.getRequestNumber(), ex.getMessage());
            emailFailed = true;
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "We sent your request to the right team",
                "requestNumber", request.getRequestNumber(),
                "emailWarning", emailFailed
        ));
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
