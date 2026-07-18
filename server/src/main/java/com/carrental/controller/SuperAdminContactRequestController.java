package com.carrental.controller;

import com.carrental.entity.ContactRequest;
import com.carrental.entity.SupportTicket;
import com.carrental.repository.ContactRequestRepository;
import com.carrental.repository.SupportTicketRepository;
import com.carrental.service.SupportRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Super Admin management of {@link ContactRequest}s — kept as its own
 * controller (rather than folded into the already-large SuperAdminController)
 * to keep Contact Requests structurally distinct from Support Tickets, which
 * is the whole point of this module split.
 */
@RestController
@RequestMapping("/api/super-admin/contact-requests")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminContactRequestController {

    private final ContactRequestRepository contactRequestRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final SupportRoutingService supportRoutingService;

    @GetMapping
    public ResponseEntity<List<ContactRequest>> list(@RequestParam(required = false) String status) {
        List<ContactRequest> requests = status != null
                ? contactRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                : contactRequestRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContactRequest> get(@PathVariable Long id) {
        return ResponseEntity.ok(contactRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contact request not found")));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ContactRequest> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ContactRequest request = contactRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contact request not found"));
        if (body.containsKey("status")) {
            request.setStatus(body.get("status"));
        }
        return ResponseEntity.ok(contactRequestRepository.save(request));
    }

    /**
     * Explicit, auditable conversion of a contact request into a real support
     * ticket. Idempotency guard: a request can only be converted once —
     * {@code convertedTicketId} is the permanent audit trail on the source row.
     */
    @PostMapping("/{id}/convert-to-ticket")
    public ResponseEntity<Map<String, Object>> convertToTicket(@PathVariable Long id) {
        ContactRequest request = contactRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contact request not found"));
        if (request.getConvertedTicketId() != null) {
            throw new IllegalArgumentException("Contact request " + request.getRequestNumber() + " was already converted to a ticket");
        }

        String destinationEmail = supportRoutingService.resolveDestinationEmail(
                SupportRoutingService.CHANNEL_CONTACT, request.getCategory());

        SupportTicket ticket = supportTicketRepository.save(SupportTicket.builder()
                .subject(request.getSubject())
                .description(request.getMessage())
                .category(mapToTicketCategory(request.getCategory()))
                .priority(SupportTicket.Priority.MEDIUM)
                .status(SupportTicket.Status.OPEN)
                .tenant(null)
                .createdBy(request.getRequesterName())
                .contactEmail(request.getRequesterEmail())
                .channel(SupportRoutingService.CHANNEL_CONTACT)
                .destinationEmail(destinationEmail)
                .requesterName(request.getRequesterName())
                .requesterEmail(request.getRequesterEmail())
                .requesterPhone(request.getRequesterPhone())
                .build());

        request.setConvertedTicketId(ticket.getId());
        request.setStatus("CONVERTED");
        contactRequestRepository.save(request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "ticketId", ticket.getId(),
                "ticketNumber", ticket.getTicketNumber()
        ));
    }

    /**
     * Contact request categories (GENERAL/SALES/PARTNERSHIP/PRESS/LEGAL/PRIVACY/SECURITY/OTHER)
     * are a different, broader set than ticket categories — map what overlaps, default to OTHER.
     */
    private SupportTicket.Category mapToTicketCategory(String contactCategory) {
        if (contactCategory == null) return SupportTicket.Category.OTHER;
        return switch (contactCategory.toUpperCase()) {
            case "TECHNICAL" -> SupportTicket.Category.TECHNICAL;
            case "BILLING" -> SupportTicket.Category.BILLING;
            default -> SupportTicket.Category.OTHER;
        };
    }
}
