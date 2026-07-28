package com.carrental.controller;

import com.carrental.entity.ContactRequest;
import com.carrental.repository.ContactRequestRepository;
import com.carrental.service.PlatformEmailService;
import com.carrental.service.SupportRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the public "Contact Us" endpoint. The production
 * bug this guards against: both notification emails used to run
 * synchronously inside this controller's request thread/transaction, so a
 * slow or unreachable ZeptoMail (or a hung DNS resolution to it, which
 * HttpEmailProvider's HttpClient timeout does not bound) held the HTTP
 * response open well past the frontend's own timeout — surfacing there as
 * "API server unavailable" even though the request had already been saved.
 * Emails must now run on the injected executor, never inline.
 */
@ExtendWith(MockitoExtension.class)
class PublicContactControllerTest {

    @Mock private ContactRequestRepository contactRequestRepository;
    @Mock private SupportRoutingService supportRoutingService;
    @Mock private PlatformEmailService platformEmailService;
    @Mock private Executor emailDispatchExecutor;

    private PublicContactController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicContactController(
                contactRequestRepository, supportRoutingService, platformEmailService, emailDispatchExecutor);
    }

    private Map<String, Object> validPayload() {
        return Map.of(
                "requesterName", "Abdellah",
                "requesterEmail", "user@example.com",
                "requesterPhone", "+212600000000",
                "category", "GENERAL",
                "subject", "Demo request please",
                "message", "This is a long enough test message for validation.");
    }

    @Test
    void validSubmission_savesRequestAndReturns200WithoutBlockingOnEmail() {
        when(supportRoutingService.resolveDestinationEmail(SupportRoutingService.CHANNEL_CONTACT, "GENERAL"))
                .thenReturn("support@innovacar.app");
        when(contactRequestRepository.save(any(ContactRequest.class))).thenAnswer(inv -> {
            ContactRequest saved = inv.getArgument(0);
            saved.setId(1L);
            saved.setRequestNumber("CR-TEST0001");
            return saved;
        });

        ResponseEntity<Map<String, Object>> response = controller.submitContactForm(validPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("requestNumber", "CR-TEST0001");

        // The two email sends must be handed to the executor, never invoked
        // directly on the calling thread from the controller itself.
        verify(emailDispatchExecutor).execute(any(Runnable.class));
        verify(platformEmailService, never()).sendContactRequestCreatedInternal(any());
        verify(platformEmailService, never()).sendContactRequestConfirmation(any());
    }

    @Test
    void executorSaturated_stillReturnsSuccessSinceRequestIsAlreadySaved() {
        when(supportRoutingService.resolveDestinationEmail(anyString(), anyString())).thenReturn("support@innovacar.app");
        when(contactRequestRepository.save(any(ContactRequest.class))).thenAnswer(inv -> {
            ContactRequest saved = inv.getArgument(0);
            saved.setRequestNumber("CR-TEST0002");
            return saved;
        });
        org.mockito.Mockito.doThrow(new RejectedExecutionException("queue full"))
                .when(emailDispatchExecutor).execute(any(Runnable.class));

        ResponseEntity<Map<String, Object>> response = controller.submitContactForm(validPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("requestNumber", "CR-TEST0002");
    }

    @Test
    void missingSubject_rejectedBeforeSaving() {
        Map<String, Object> payload = new java.util.HashMap<>(validPayload());
        payload.remove("subject");

        assertThatThrownBy(() -> controller.submitContactForm(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    void tooShortSubject_rejected() {
        Map<String, Object> payload = new java.util.HashMap<>(validPayload());
        payload.put("subject", "hi");

        assertThatThrownBy(() -> controller.submitContactForm(payload))
                .isInstanceOf(IllegalArgumentException.class);
        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    void invalidEmail_rejected() {
        Map<String, Object> payload = new java.util.HashMap<>(validPayload());
        payload.put("requesterEmail", "not-an-email");

        assertThatThrownBy(() -> controller.submitContactForm(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    void tooShortMessage_rejected() {
        Map<String, Object> payload = new java.util.HashMap<>(validPayload());
        payload.put("message", "short");

        assertThatThrownBy(() -> controller.submitContactForm(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message");
        verify(contactRequestRepository, never()).save(any());
    }

    @Test
    void dispatchedEmailFailure_isCaughtInsideTheExecutorTask_neverPropagatesToCaller() {
        when(supportRoutingService.resolveDestinationEmail(anyString(), anyString())).thenReturn("support@innovacar.app");
        when(contactRequestRepository.save(any(ContactRequest.class))).thenAnswer(inv -> {
            ContactRequest saved = inv.getArgument(0);
            saved.setRequestNumber("CR-TEST0003");
            return saved;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("ZeptoMail unreachable"))
                .when(platformEmailService).sendContactRequestCreatedInternal(any());

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        ResponseEntity<Map<String, Object>> response = controller.submitContactForm(validPayload());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(emailDispatchExecutor).execute(taskCaptor.capture());
        // Running the captured task synchronously here (simulating the executor
        // thread) must not throw, even though the internal-notification send
        // above is stubbed to fail — the confirmation send must still be attempted.
        taskCaptor.getValue().run();
        verify(platformEmailService).sendContactRequestConfirmation(any());
    }
}
