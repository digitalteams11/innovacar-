package com.carrental.service;

import com.carrental.dto.ai.AiExecuteResponse;
import com.carrental.entity.AiSettings;
import com.carrental.entity.SupportMessage;
import com.carrental.entity.SupportTicket;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.repository.SupportMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Backs the "Generate AI Draft" button on the Super Admin ticket detail page —
 * wires up the {@code SUPPORT_REPLY_DRAFT} automation, seeded since V48 as
 * "Not yet triggered by the support module" and never actually called until
 * now. Also the first thing that actually reads
 * {@link AiSettings#getEnableSupportAssistant()}, which existed as a settings
 * toggle with nothing behind it before this.
 *
 * <p>Deliberately drafts only — it never creates a {@link SupportMessage} or
 * sends anything itself. The draft is returned to the admin to review and
 * edit in the existing reply box; sending still goes through the normal
 * {@code POST /tickets/{id}/messages} path, so nothing an AI wrote can reach
 * a customer without a human choosing to send it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportAiAssistantService {

    public static final String AUTOMATION_CODE = "SUPPORT_REPLY_DRAFT";

    private static final String SYSTEM_INSTRUCTION = """
            You are a professional, empathetic customer support agent for Innovacar, a car rental \
            management SaaS platform. You are drafting a reply to a support ticket for a staff member \
            to review before sending — you are not sending it yourself.

            Rules:
            - Only use facts present in the ticket subject, description, and message thread below. Never \
              invent account details, dates, prices, or policies you have not been given.
            - If the ticket does not contain enough information to give a specific answer, write a short \
              reply asking the customer for the missing detail instead of guessing.
            - Keep the tone helpful, concise, and professional. Do not use emoji.
            - Do not include a greeting salutation or sign-off — the reply box already has its own \
              header and footer.
            """;

    private final AiGatewayService aiGatewayService;
    private final AiSettingsRepository aiSettingsRepository;
    private final SupportMessageRepository supportMessageRepository;

    public record DraftResult(String draft, String requestId) {}

    @Transactional
    public DraftResult draftReply(SupportTicket ticket) {
        AiSettings settings = aiSettingsRepository.findAll().stream().findFirst().orElse(null);
        if (settings == null || !Boolean.TRUE.equals(settings.getGlobalEnabled())) {
            throw AiServiceException.disabled();
        }
        if (!Boolean.TRUE.equals(settings.getEnableSupportAssistant())) {
            throw AiServiceException.supportAssistantDisabled();
        }

        String userPrompt = buildPrompt(ticket);
        AiExecuteResponse result = aiGatewayService.execute(AUTOMATION_CODE, SYSTEM_INSTRUCTION, userPrompt);
        return new DraftResult(result.getContent(), result.getRequestId());
    }

    private String buildPrompt(SupportTicket ticket) {
        List<SupportMessage> messages = supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("Ticket #").append(ticket.getTicketNumber()).append('\n');
        sb.append("Subject: ").append(nullSafe(ticket.getSubject())).append('\n');
        if (ticket.getCategory() != null) sb.append("Category: ").append(ticket.getCategory()).append('\n');
        if (ticket.getPriority() != null) sb.append("Priority: ").append(ticket.getPriority()).append('\n');
        sb.append("Description: ").append(nullSafe(ticket.getDescription())).append("\n\n");

        if (messages.isEmpty()) {
            sb.append("(No messages have been exchanged on this ticket yet.)\n");
        } else {
            sb.append("Message thread (oldest first):\n");
            for (SupportMessage message : messages) {
                sb.append('[').append(nullSafe(message.getSenderType())).append("] ")
                        .append(nullSafe(message.getSenderName())).append(": ")
                        .append(nullSafe(message.getMessage())).append('\n');
            }
        }

        sb.append("\nDraft the next reply from support.");
        return sb.toString();
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }
}
