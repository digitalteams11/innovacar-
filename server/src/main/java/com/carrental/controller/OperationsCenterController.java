package com.carrental.controller;

import com.carrental.entity.*;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import com.carrental.service.NotificationService;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SupportRoutingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@RestController
@RequestMapping("/api/operations-center")
@RequiredArgsConstructor
public class OperationsCenterController {
    private final SupportTicketRepository ticketRepository;
    private final KnowledgeArticleRepository articleRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final LegalAcceptanceRepository acceptanceRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserSessionRepository sessionRepository;
    private final GpsSettingsRepository gpsSettingsRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    private final SupportMessageRepository messageRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final SupportRoutingService supportRoutingService;
    private final PlatformEmailService platformEmailService;

    @Qualifier("emailDispatchExecutor")
    private final Executor emailDispatchExecutor;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> overview() {
        User user = currentUser();
        Long tenantId = TenantContext.getCurrentTenantId();
        List<SupportTicket> tickets = ticketRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Set<Long> acceptedDocuments = new HashSet<>();
        acceptanceRepository.findByTenantIdAndUserId(tenantId, user.getId())
                .forEach(value -> acceptedDocuments.add(value.getDocument().getId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tickets", tickets.stream().filter(this::isSupport).map(this::ticketMap).toList());
        data.put("complaints", tickets.stream().filter(this::isComplaint).map(this::ticketMap).toList());
        data.put("knowledge", articleRepository.findByPublishedTrueOrderByCategoryAscTitleAsc().stream()
                .map(this::articleMap).toList());
        data.put("legalDocuments", legalDocumentRepository.findByActiveTrueOrderByTitleAsc().stream()
                .map(document -> legalMap(document, acceptedDocuments.contains(document.getId()))).toList());
        data.put("security", securityMap(user, tenantId));
        data.put("health", healthMap(tenantId));
        return ResponseEntity.ok(success("Operations center loaded successfully", data));
    }

    @PostMapping("/tickets")
    @Transactional
    public ResponseEntity<Map<String, Object>> createTicket(@RequestBody Map<String, Object> request) {
        User user = currentUser();
        Long tenantId = TenantContext.getCurrentTenantId();
        String subject = required(request, "subject");
        String description = required(request, "description");
        String kind = text(request.get("kind"), "SUPPORT").toUpperCase(Locale.ROOT);
        String categoryRaw = text(request.get("category"), "OTHER").toUpperCase(Locale.ROOT);
        String priorityRaw = text(request.get("priority"), "MEDIUM").toUpperCase(Locale.ROOT);
        String channel = text(request.get("channel"), null);
        boolean isComplaintKind = "COMPLAINT".equals(kind);

        SupportTicket.Category category = parseEnumOrUnknown(SupportTicket.Category.class, categoryRaw);
        SupportTicket.Priority priority = parseEnumOrUnknown(SupportTicket.Priority.class, priorityRaw);

        String destinationEmail = supportRoutingService.resolveDestinationEmail(channel, category.name());

        // Complaints are routed via the channel field (not a category-name prefix hack) so
        // that category can stay a clean enum.
        String resolvedChannel = isComplaintKind
                ? "COMPLAINT"
                : channel != null ? channel.toUpperCase(Locale.ROOT) : SupportRoutingService.CHANNEL_SUPPORT;

        SupportTicket ticket = ticketRepository.save(SupportTicket.builder()
                .subject(subject)
                .description(description)
                .category(category)
                .priority(priority)
                .status(SupportTicket.Status.OPEN)
                .tenant(tenantRepository.getReferenceById(tenantId))
                .createdBy(displayName(user))
                .contactEmail(user.getEmail())
                .channel(resolvedChannel)
                .destinationEmail(destinationEmail)
                .requesterName(displayName(user))
                .requesterEmail(user.getEmail())
                .build());

        notificationService.createNotification(
                "COMPLAINT".equals(kind) ? "Complaint submitted" : "Support request submitted",
                ticket.getTicketNumber() + " is now open.",
                Notification.NotificationType.SUPPORT_TICKET_CREATED,
                null,
                tenantId
        );

        // Force-initialize the lazy tenant proxy now, while this method's own
        // persistence context/session is still open — sendSupportTicketCreatedInternal
        // reads ticket.getTenant().getName(), and once the ticket is handed to the
        // dispatch executor below it runs on a different thread with no session at
        // all, so a still-unloaded lazy proxy would throw LazyInitializationException.
        if (ticket.getTenant() != null) {
            ticket.getTenant().getName();
        }

        // Both ZeptoMail sends previously ran synchronously here, inside this
        // request's @Transactional method — HttpEmailProvider has its own 10s HTTP
        // timeout each, so a slow/degraded provider could hold this method's Hikari
        // connection (and the request thread) for up to ~20s combined, colliding
        // with the frontend's 20s axios timeout and surfacing as "API server
        // unavailable" even though the ticket had already been persisted. Dispatching
        // to the bounded emailDispatchExecutor means this method returns — and
        // releases its connection — as soon as the ticket is committed, regardless
        // of ZeptoMail's latency. Each send still runs in its own REQUIRES_NEW
        // transaction (see PlatformEmailService) purely for its own email-log/status
        // bookkeeping, and any failure there is logged, never able to roll back or
        // delay the ticket creation that already succeeded.
        dispatchTicketEmails(ticket);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "COMPLAINT".equals(kind) ? "Complaint submitted successfully" : "Support request submitted successfully",
                "ticket", ticketMap(ticket)
        ));
    }

    /**
     * Submits both post-creation ticket emails to the bounded dispatch executor
     * instead of sending them on the calling (request/transactional) thread. A full
     * queue (executor saturated) is treated the same as a delivery failure — logged
     * and dropped, never blocking the ticket-creation response that already succeeded.
     */
    private void dispatchTicketEmails(SupportTicket ticket) {
        try {
            emailDispatchExecutor.execute(() -> {
                try {
                    platformEmailService.sendSupportTicketCreatedInternal(ticket);
                } catch (Exception e) {
                    log.error("[SUPPORT_TICKET_EMAIL] Internal notification failed for ticket [{}]: {}",
                            ticket.getTicketNumber(), e.getMessage(), e);
                }
                try {
                    platformEmailService.sendSupportTicketConfirmation(ticket);
                } catch (Exception e) {
                    log.error("[SUPPORT_TICKET_EMAIL] Requester confirmation failed for ticket [{}]: {}",
                            ticket.getTicketNumber(), e.getMessage(), e);
                }
            });
        } catch (RejectedExecutionException rex) {
            log.warn("[SUPPORT_TICKET_EMAIL] Dispatch queue full — notifications for ticket [{}] were not sent",
                    ticket.getTicketNumber());
        }
    }

    @GetMapping("/tickets/{ticketId}/messages")
    @Transactional
    public List<Map<String, Object>> messages(@PathVariable Long ticketId) {
        SupportTicket ticket = tenantTicket(ticketId);
        List<SupportMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        messages.stream()
                .filter(message -> "SUPPORT".equals(message.getSenderType()) && message.getReadAt() == null)
                .forEach(message -> message.setReadAt(LocalDateTime.now()));
        messageRepository.saveAll(messages);
        return messages.stream().map(this::messageMap).toList();
    }

    @PostMapping("/tickets/{ticketId}/messages")
    @Transactional
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable Long ticketId,
            @RequestBody Map<String, Object> request) {
        SupportTicket ticket = tenantTicket(ticketId);
        User user = currentUser();
        String message = text(request.get("message"), "").trim();
        String attachmentData = text(request.get("attachmentData"), "").trim();
        if (message.isBlank() && attachmentData.isBlank()) {
            throw new IllegalArgumentException("A message or attachment is required");
        }
        if (attachmentData.length() > 7_000_000) {
            throw new IllegalArgumentException("Attachment must not exceed 5 MB");
        }

        SupportMessage saved = messageRepository.save(SupportMessage.builder()
                .ticket(ticket)
                .senderId(user.getId())
                .senderName(displayName(user))
                .senderType("AGENCY")
                .message(message)
                .attachmentName(text(request.get("attachmentName"), null))
                .attachmentType(text(request.get("attachmentType"), null))
                .attachmentData(attachmentData.isBlank() ? null : attachmentData)
                .readAt(LocalDateTime.now())
                .build());
        ticket.setStatus(SupportTicket.Status.OPEN);
        ticketRepository.save(ticket);
        return ResponseEntity.ok(Map.of("success", true, "message", "Message sent successfully", "item", messageMap(saved)));
    }

    @PostMapping("/legal/{documentId}/accept")
    @Transactional
    public ResponseEntity<Map<String, Object>> acceptLegalDocument(
            @PathVariable Long documentId,
            HttpServletRequest request) {
        User user = currentUser();
        Long tenantId = TenantContext.getCurrentTenantId();
        LegalDocument document = legalDocumentRepository.findById(documentId)
                .filter(value -> Boolean.TRUE.equals(value.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("Legal document not found"));

        acceptanceRepository.findByUserIdAndDocumentId(user.getId(), documentId).orElseGet(() ->
                acceptanceRepository.save(LegalAcceptance.builder()
                        .tenant(tenantRepository.getReferenceById(tenantId))
                        .userId(user.getId())
                        .document(document)
                        .ipAddress(clientIp(request))
                        .userAgent(request.getHeader("User-Agent"))
                        .build())
        );

        return ResponseEntity.ok(Map.of("success", true, "message", "Acceptance recorded successfully"));
    }

    @DeleteMapping("/security/sessions/{sessionId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> revokeSession(@PathVariable Long sessionId) {
        User user = currentUser();
        UserSession session = sessionRepository.findById(sessionId)
                .filter(value -> Objects.equals(value.getUserId(), user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        session.setRevoked(true);
        sessionRepository.save(session);
        return ResponseEntity.ok(Map.of("success", true, "message", "Session revoked successfully"));
    }

    private Map<String, Object> securityMap(User user, Long tenantId) {
        List<Map<String, Object>> sessions = sessionRepository
                .findByUserIdAndRevokedFalseAndExpiresAtAfter(user.getId(), LocalDateTime.now())
                .stream().map(session -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", session.getId());
                    value.put("ipAddress", session.getIpAddress());
                    value.put("userAgent", session.getUserAgent());
                    value.put("location", session.getLocation());
                    value.put("createdAt", session.getCreatedAt());
                    value.put("expiresAt", session.getExpiresAt());
                    return value;
                }).toList();

        List<Map<String, Object>> activity = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().limit(20).map(log -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", log.getId());
                    value.put("action", log.getAction());
                    value.put("description", log.getDescription());
                    value.put("performedBy", log.getPerformedBy());
                    value.put("ipAddress", log.getIpAddress());
                    value.put("successful", log.getIsSuccess());
                    value.put("createdAt", log.getCreatedAt());
                    return value;
                }).toList();

        Map<String, Object> security = new LinkedHashMap<>();
        security.put("sessions", sessions);
        security.put("activity", activity);
        security.put("failedLoginAttempts", Optional.ofNullable(user.getFailedLoginAttempts()).orElse(0));
        security.put("lastLoginAt", user.getLastLoginAt());
        security.put("devices", trustedDeviceRepository.findByUserIdOrderByLastSeenAtDesc(user.getId()).stream()
                .map(device -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", device.getId());
                    value.put("name", device.getDeviceName());
                    value.put("browser", device.getBrowser());
                    value.put("operatingSystem", device.getOperatingSystem());
                    value.put("ipAddress", device.getLastIpAddress());
                    value.put("trusted", device.getTrusted());
                    value.put("blocked", device.getBlocked());
                    value.put("lastSeenAt", device.getLastSeenAt());
                    return value;
                }).toList());
        security.put("loginHistory", loginAttemptRepository.findByUserIdOrderByAttemptedAtDesc(user.getId()).stream()
                .limit(20)
                .map(attempt -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", attempt.getId());
                    value.put("ipAddress", attempt.getIpAddress());
                    value.put("success", attempt.getSuccessful());
                    value.put("suspicious", attempt.getSuspicious());
                    value.put("failureReason", attempt.getFailureReason());
                    value.put("createdAt", attempt.getAttemptedAt());
                    return value;
                }).toList());
        security.put("passwordExpiresAt", user.getPasswordExpiresAt());
        return security;
    }

    private Map<String, Object> healthMap(Long tenantId) {
        Optional<TenantSettings> settings = tenantSettingsRepository.findByTenantId(tenantId);
        boolean gpsConfigured = gpsSettingsRepository.existsByTenantId(tenantId);
        boolean emailConfigured = settings.map(value -> value.getSmtpHost() != null && !value.getSmtpHost().isBlank()).orElse(false);

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("database", service("OPERATIONAL", "Tenant data is reachable"));
        health.put("api", service("OPERATIONAL", "Application API is responding"));
        health.put("email", service(emailConfigured ? "OPERATIONAL" : "NOT_CONFIGURED",
                emailConfigured ? "SMTP delivery is configured" : "Connect an SMTP provider in Settings"));
        health.put("gps", service(gpsConfigured ? "OPERATIONAL" : "NOT_CONFIGURED",
                gpsConfigured ? "GPS provider is configured" : "GPS is optional and not configured"));
        health.put("notifications", service("OPERATIONAL", "In-app notification delivery is active"));
        health.put("contracts", service("OPERATIONAL", "Contract services are available"));
        return health;
    }

    private Map<String, Object> service(String status, String message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", status);
        value.put("message", message);
        return value;
    }

    private Map<String, Object> ticketMap(SupportTicket ticket) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", ticket.getId());
        value.put("ticketNumber", ticket.getTicketNumber() == null ? "LEGACY-" + ticket.getId() : ticket.getTicketNumber());
        value.put("subject", ticket.getSubject());
        value.put("description", ticket.getDescription());
        value.put("status", ticket.getStatus() != null ? ticket.getStatus().name() : null);
        value.put("priority", ticket.getPriority() != null ? ticket.getPriority().name() : null);
        value.put("category", ticket.getCategory() != null ? ticket.getCategory().name() : null);
        value.put("channel", ticket.getChannel());
        value.put("emailStatus", ticket.getEmailStatus());
        value.put("createdAt", ticket.getCreatedAt());
        value.put("updatedAt", ticket.getUpdatedAt());
        value.put("resolution", ticket.getResolution());
        return value;
    }

    private Map<String, Object> messageMap(SupportMessage message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", message.getId());
        value.put("senderName", message.getSenderName());
        value.put("senderType", message.getSenderType());
        value.put("message", text(message.getMessage(), ""));
        value.put("attachmentName", text(message.getAttachmentName(), ""));
        value.put("attachmentType", text(message.getAttachmentType(), ""));
        value.put("attachmentData", text(message.getAttachmentData(), ""));
        value.put("read", message.getReadAt() != null);
        value.put("createdAt", message.getCreatedAt());
        return value;
    }

    private Map<String, Object> articleMap(KnowledgeArticle article) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", article.getId());
        value.put("title", text(article.getTitle(), "Untitled guide"));
        value.put("category", text(article.getCategory(), "General"));
        value.put("summary", text(article.getSummary(), ""));
        value.put("content", text(article.getContent(), ""));
        return value;
    }

    private SupportTicket tenantTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .filter(ticket -> ticket.getTenant() != null
                        && Objects.equals(ticket.getTenant().getId(), TenantContext.getCurrentTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Support request not found"));
    }

    private Map<String, Object> legalMap(LegalDocument document, boolean accepted) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", document.getId());
        value.put("code", text(document.getCode(), ""));
        value.put("title", text(document.getTitle(), "Legal document"));
        value.put("version", text(document.getVersion(), "1.0"));
        value.put("content", text(document.getContent(), ""));
        value.put("publishedAt", document.getPublishedAt());
        value.put("accepted", accepted);
        return value;
    }

    private Map<String, Object> success(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    private boolean isComplaint(SupportTicket ticket) {
        return "COMPLAINT".equals(ticket.getChannel());
    }

    private boolean isSupport(SupportTicket ticket) {
        return !isComplaint(ticket);
    }

    private <E extends Enum<E>> E parseEnumOrUnknown(Class<E> enumType, String raw) {
        try {
            return Enum.valueOf(enumType, raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            try {
                return Enum.valueOf(enumType, "UNKNOWN");
            } catch (IllegalArgumentException unknownMissing) {
                throw ex;
            }
        }
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user;
        throw new IllegalStateException("Authenticated user not found");
    }

    private String displayName(User user) {
        String name = (text(user.getFirstName(), "") + " " + text(user.getLastName(), "")).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private String required(Map<String, Object> request, String key) {
        String value = text(request.get(key), "").trim();
        if (value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
