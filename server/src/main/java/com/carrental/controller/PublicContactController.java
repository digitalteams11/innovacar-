package com.carrental.controller;

import com.carrental.entity.SupportTicket;
import com.carrental.repository.SupportTicketRepository;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SupportRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

/**
 * Public, unauthenticated entry point for the "Contact" channel of the Help & Support Center.
 * Anonymous visitors (leads, prospects) can reach the sales/general inbox without logging in.
 * Every submission is still persisted as a {@link SupportTicket} so it shows up in the
 * Super Admin dashboard like any other ticket.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicContactController {

    private final SupportTicketRepository ticketRepository;
    private final SupportRoutingService supportRoutingService;
    private final PlatformEmailService platformEmailService;

    @PostMapping("/contact")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitContactForm(@RequestBody Map<String, Object> request) {
        String subject = required(request, "subject");
        String message = required(request, "message");
        String requesterEmail = required(request, "requesterEmail");
        String requesterName = text(request.get("requesterName"), "Anonymous");
        String requesterPhone = text(request.get("requesterPhone"), null);
        String category = text(request.get("category"), "GENERAL").toUpperCase(Locale.ROOT);

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

        SupportTicket ticket = ticketRepository.save(SupportTicket.builder()
                .subject(subject)
                .description(message)
                .category(category)
                .priority("NORMAL")
                .status("OPEN")
                .tenant(null)
                .createdBy(requesterName)
                .contactEmail(requesterEmail)
                .channel(SupportRoutingService.CHANNEL_CONTACT)
                .destinationEmail(destinationEmail)
                .requesterName(requesterName)
                .requesterEmail(requesterEmail)
                .requesterPhone(requesterPhone)
                .build());

        platformEmailService.sendSupportTicketCreatedInternal(ticket);
        platformEmailService.sendSupportTicketConfirmation(ticket);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "We sent your request to the right team",
                "ticketNumber", ticket.getTicketNumber()
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
