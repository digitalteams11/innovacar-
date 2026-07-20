package com.carrental.service;

import com.carrental.dto.ai.AiExecuteResponse;
import com.carrental.entity.AiSettings;
import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.User;
import com.carrental.exception.AiServiceException;
import com.carrental.repository.AiSettingsRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock private AiGatewayService aiGatewayService;
    @Mock private AiSettingsRepository aiSettingsRepository;
    @Mock private AiPromptSanitizer aiPromptSanitizer;
    @Mock private AiUsageLogService aiUsageLogService;
    @Mock private FeatureAccessService featureAccessService;
    @Mock private AiKnowledgeService aiKnowledgeService;

    @InjectMocks private AiAssistantService aiAssistantService;

    private User user;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setId(42L);
        tenant.setName("Acme Rentals");
        user = new User();
        user.setId(7L);
        user.setRole(Role.SUPER_ADMIN);
        user.setTenant(tenant);
        TenantContext.setCurrentTenantId(42L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void chat_routesThroughGatewayWithChatAssistantAutomationCode() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(
                AiSettings.builder().globalEnabled(true).enableChat(true).dailyRequestLimit(200).build()));
        when(aiPromptSanitizer.sanitize(anyString())).thenReturn("sanitized message");
        when(aiKnowledgeService.buildSystemInstruction(any(), any(), any(), any(), any())).thenReturn("system prompt");
        when(aiUsageLogService.countSince(any(), any())).thenReturn(0L);
        when(aiUsageLogService.countForAgencySince(any(), any())).thenReturn(0L);
        when(aiGatewayService.execute(AiAssistantService.AUTOMATION_CODE, "system prompt", "sanitized message"))
                .thenReturn(AiExecuteResponse.builder().success(true).content("Hi there").build());
        when(aiKnowledgeService.suggestedActions(any(), any())).thenReturn(List.of());
        when(aiKnowledgeService.sources(any())).thenReturn(List.of());

        AiAssistantService.ChatResult result = aiAssistantService.chat(user, "dashboard", "hello", "/dashboard", null);

        assertThat(result.reply()).isEqualTo("Hi there");
    }

    @Test
    void chat_globalDisabled_throwsBeforeCallingGateway() {
        when(aiSettingsRepository.findAll()).thenReturn(List.of(AiSettings.builder().globalEnabled(false).build()));

        assertThatThrownBy(() -> aiAssistantService.chat(user, "dashboard", "hello", "/dashboard", null))
                .isInstanceOf(AiServiceException.class)
                .extracting(e -> ((AiServiceException) e).getErrorCode())
                .isEqualTo("AI_DISABLED");
    }

    @Test
    void chat_passesUserLanguageToSystemInstruction() {
        user.setLanguage("fr");
        when(aiSettingsRepository.findAll()).thenReturn(List.of(
                AiSettings.builder().globalEnabled(true).enableChat(true).dailyRequestLimit(200).build()));
        when(aiPromptSanitizer.sanitize(anyString())).thenReturn("sanitized message");
        when(aiKnowledgeService.buildSystemInstruction(any(), any(), any(), any(), any())).thenReturn("system prompt");
        when(aiUsageLogService.countSince(any(), any())).thenReturn(0L);
        when(aiUsageLogService.countForAgencySince(any(), any())).thenReturn(0L);
        when(aiGatewayService.execute(any(), any(), any()))
                .thenReturn(AiExecuteResponse.builder().success(true).content("Bonjour").build());
        when(aiKnowledgeService.suggestedActions(any(), any())).thenReturn(List.of());
        when(aiKnowledgeService.sources(any())).thenReturn(List.of());

        aiAssistantService.chat(user, "dashboard", "hello", "/dashboard", null);

        org.mockito.Mockito.verify(aiKnowledgeService)
                .buildSystemInstruction(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq("fr"));
    }

    @Test
    void chat_agencyIdAlwaysDerivedFromTenantContext_notFromCaller() {
        // Cross-agency isolation: the rate-limit check always uses TenantContext,
        // never a caller-supplied agency id, since chat() takes no agencyId parameter.
        when(aiSettingsRepository.findAll()).thenReturn(List.of(
                AiSettings.builder().globalEnabled(true).enableChat(true).dailyRequestLimit(200).build()));
        when(aiPromptSanitizer.sanitize(anyString())).thenReturn("sanitized message");
        when(aiKnowledgeService.buildSystemInstruction(any(), any(), any(), any(), any())).thenReturn("system prompt");
        when(aiUsageLogService.countForAgencySince(org.mockito.ArgumentMatchers.eq(42L), any())).thenReturn(0L);
        when(aiUsageLogService.countSince(any(), any())).thenReturn(0L);
        when(aiGatewayService.execute(any(), any(), any()))
                .thenReturn(AiExecuteResponse.builder().success(true).content("ok").build());
        when(aiKnowledgeService.suggestedActions(any(), any())).thenReturn(List.of());
        when(aiKnowledgeService.sources(any())).thenReturn(List.of());

        aiAssistantService.chat(user, "dashboard", "hello", "/dashboard", null);

        org.mockito.Mockito.verify(aiUsageLogService).countForAgencySince(org.mockito.ArgumentMatchers.eq(42L), any());
    }
}
