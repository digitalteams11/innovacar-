package com.carrental.service;

import com.carrental.dto.ai.AiExecuteResponse;
import com.carrental.entity.AiSettings;
import com.carrental.entity.SupportMessage;
import com.carrental.entity.SupportTicket;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.repository.SupportMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportAiAssistantServiceTest {

    @Mock private AiGatewayService aiGatewayService;
    @Mock private AiSettingsRepository aiSettingsRepository;
    @Mock private SupportMessageRepository supportMessageRepository;

    @InjectMocks private SupportAiAssistantService service;

    @Captor private ArgumentCaptor<String> userPromptCaptor;

    private SupportTicket ticket(String subject, String description) {
        SupportTicket t = new SupportTicket();
        t.setId(1L);
        t.setTicketNumber("RC-TEST0001");
        t.setSubject(subject);
        t.setDescription(description);
        t.setCategory(SupportTicket.Category.BILLING);
        t.setPriority(SupportTicket.Priority.HIGH);
        return t;
    }

    @Test
    void globalAiDisabled_throwsBeforeCallingGateway() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(
                AiSettings.builder().globalEnabled(false).enableSupportAssistant(true).build()));

        assertThatThrownBy(() -> service.draftReply(ticket("s", "d")))
                .isInstanceOf(AiServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AI_DISABLED");
    }

    @Test
    void supportAssistantToggleOff_throwsEvenWhenGlobalAiEnabled() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(
                AiSettings.builder().globalEnabled(true).enableSupportAssistant(false).build()));

        assertThatThrownBy(() -> service.draftReply(ticket("s", "d")))
                .isInstanceOf(AiServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AI_SUPPORT_ASSISTANT_DISABLED");
    }

    @Test
    void enabled_callsGatewayWithSupportReplyDraftCodeAndReturnsDraft() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(
                AiSettings.builder().globalEnabled(true).enableSupportAssistant(true).build()));
        when(supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(aiGatewayService.execute(eq("SUPPORT_REPLY_DRAFT"), anyString(), anyString()))
                .thenReturn(AiExecuteResponse.builder().success(true).requestId("req-1")
                        .content("Thanks for reaching out, here is an update...").build());

        SupportAiAssistantService.DraftResult result = service.draftReply(ticket("Refund question", "Client wants a refund"));

        assertThat(result.draft()).isEqualTo("Thanks for reaching out, here is an update...");
        assertThat(result.requestId()).isEqualTo("req-1");
    }

    @Test
    void promptIncludesTicketFactsAndMessageThread_neverInventsDetails() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(
                AiSettings.builder().globalEnabled(true).enableSupportAssistant(true).build()));
        SupportMessage msg = SupportMessage.builder()
                .senderType("CUSTOMER").senderName("Jane Client").message("When will my deposit be refunded?").build();
        when(supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(msg));
        when(aiGatewayService.execute(anyString(), anyString(), userPromptCaptor.capture()))
                .thenReturn(AiExecuteResponse.builder().success(true).requestId("r").content("draft").build());

        service.draftReply(ticket("Deposit refund", "Client requests deposit refund status"));

        String prompt = userPromptCaptor.getValue();
        assertThat(prompt).contains("Deposit refund");
        assertThat(prompt).contains("Client requests deposit refund status");
        assertThat(prompt).contains("Jane Client");
        assertThat(prompt).contains("When will my deposit be refunded?");
    }
}
