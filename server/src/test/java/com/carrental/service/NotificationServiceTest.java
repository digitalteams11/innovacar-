package com.carrental.service;

import com.carrental.entity.Notification;
import com.carrental.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private SseService sseService;

    private NotificationService service;

    private NotificationService newService() {
        return new NotificationService(notificationRepository, sseService);
    }

    @Test
    void clientInformationSubmittedNotificationCarriesTheRequestIdAndTargetUrl() {
        service = newService();
        when(notificationRepository.existsByTenantIdAndTypeAndEntityIdAndCreatedAtAfter(any(), any(), any(), any()))
                .thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification saved = service.notifyClientInformationSubmitted(
                "Client information submitted", "King submitted their information for review.",
                45L, 7L);

        assertThat(saved).isNotNull();
        assertThat(saved.getType()).isEqualTo(Notification.NotificationType.CLIENT_INFORMATION_SUBMITTED);
        assertThat(saved.getEntityType()).isEqualTo("CLIENT_INFORMATION_REQUEST");
        assertThat(saved.getEntityId()).isEqualTo(45L);
        assertThat(saved.getActionUrl()).isEqualTo("/client-information-requests?requestId=45");
        assertThat(saved.getTenantId()).isEqualTo(7L);
        assertThat(saved.getRead()).isFalse();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getActionUrl()).contains("45");
    }

    @Test
    void toResponseMapNeverStripsEntityIdOrActionUrl() {
        service = newService();
        Notification n = Notification.builder()
                .id(1L).title("Client information submitted").message("King submitted their information for review.")
                .type(Notification.NotificationType.CLIENT_INFORMATION_SUBMITTED)
                .severity(Notification.Severity.INFO)
                .entityType("CLIENT_INFORMATION_REQUEST").entityId(45L)
                .actionUrl("/client-information-requests?requestId=45")
                .tenantId(7L).read(false).createdAt(java.time.LocalDateTime.now())
                .build();

        var map = service.toResponseMap(n);

        assertThat(map.get("entityId")).isEqualTo(45L);
        assertThat(map.get("actionUrl")).isEqualTo("/client-information-requests?requestId=45");
        assertThat(map.get("type")).isEqualTo("CLIENT_INFORMATION_SUBMITTED");
        assertThat(map.get("read")).isEqualTo(false);
    }

    @Test
    void skipsCreationWhenTenantIdIsNull() {
        service = newService();

        Notification result = service.notifyClientInformationSubmitted("t", "m", 1L, null);

        assertThat(result).isNull();
        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void deduplicatesWithinTheSameWindowForTheSameEntity() {
        service = newService();
        when(notificationRepository.existsByTenantIdAndTypeAndEntityIdAndCreatedAtAfter(any(), any(), any(), any()))
                .thenReturn(true);

        Notification result = service.notifyClientInformationSubmitted("t", "m", 45L, 7L);

        assertThat(result).isNull();
        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.never()).save(any());
    }
}
