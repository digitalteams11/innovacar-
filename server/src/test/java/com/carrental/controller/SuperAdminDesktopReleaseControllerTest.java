package com.carrental.controller;

import com.carrental.entity.DesktopRelease;
import com.carrental.entity.Role;
import com.carrental.entity.User;
import com.carrental.repository.DesktopDownloadEventRepository;
import com.carrental.repository.DesktopReleaseRepository;
import com.carrental.service.DesktopReleaseValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminDesktopReleaseControllerTest {

    @Mock private DesktopReleaseRepository desktopReleaseRepository;
    @Mock private DesktopDownloadEventRepository desktopDownloadEventRepository;

    private final DesktopReleaseValidator validator = new DesktopReleaseValidator(
            "github.com,objects.githubusercontent.com,r2.cloudflarestorage.com,api.innovacar.app");

    private SuperAdminDesktopReleaseController controller;

    @BeforeEach
    void setUp() {
        controller = new SuperAdminDesktopReleaseController(desktopReleaseRepository, desktopDownloadEventRepository, validator);
        User admin = User.builder().id(1L).email("admin@innovacar.app").role(Role.SUPER_ADMIN).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("version", "1.2.0");
        body.put("semanticVersion", "1.2.0");
        body.put("fileName", "Innovacar-Setup-1.2.0.exe");
        body.put("downloadUrl", "https://github.com/innovacar/desktop/releases/download/v1.2.0/setup.exe");
        body.put("sha256", "a".repeat(64));
        return body;
    }

    @Test
    void create_savesAsDraft_neverPublishedDirectly() {
        when(desktopReleaseRepository.save(any(DesktopRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.create(validPayload());

        ArgumentCaptor<DesktopRelease> captor = ArgumentCaptor.forClass(DesktopRelease.class);
        verify(desktopReleaseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DesktopRelease.Status.DRAFT);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("admin@innovacar.app");
    }

    @Test
    void create_rejectsInvalidSemanticVersion() {
        Map<String, Object> body = validPayload();
        body.put("semanticVersion", "not-a-version");

        assertThatThrownBy(() -> controller.create(body)).isInstanceOf(IllegalArgumentException.class);
        verify(desktopReleaseRepository, never()).save(any());
    }

    @Test
    void create_rejectsDisallowedDownloadDomain() {
        Map<String, Object> body = validPayload();
        body.put("downloadUrl", "https://evil-attacker.com/setup.exe");

        assertThatThrownBy(() -> controller.create(body)).isInstanceOf(IllegalArgumentException.class);
        verify(desktopReleaseRepository, never()).save(any());
    }

    @Test
    void create_rejectsNonHttpsScheme() {
        Map<String, Object> body = validPayload();
        body.put("downloadUrl", "javascript:alert(1)");

        assertThatThrownBy(() -> controller.create(body)).isInstanceOf(IllegalArgumentException.class);
        verify(desktopReleaseRepository, never()).save(any());
    }

    @Test
    void publish_setsPublishedStatusAndTimestamp() {
        DesktopRelease draft = DesktopRelease.builder()
                .id(5L).version("1.2.0").semanticVersion("1.2.0")
                .downloadUrl("https://github.com/innovacar/desktop/releases/download/v1.2.0/setup.exe")
                .status(DesktopRelease.Status.DRAFT).build();
        when(desktopReleaseRepository.findById(5L)).thenReturn(Optional.of(draft));
        when(desktopReleaseRepository.save(any(DesktopRelease.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.publish(5L);

        assertThat(draft.getStatus()).isEqualTo(DesktopRelease.Status.PUBLISHED);
        assertThat(draft.getPublishedAt()).isNotNull();
    }

    @Test
    void publish_rejectsWhenUrlNoLongerPassesValidation() {
        // Simulates a row whose URL was valid at create-time under a since-narrowed allowlist.
        DesktopRelease draft = DesktopRelease.builder()
                .id(5L).version("1.2.0").semanticVersion("1.2.0")
                .downloadUrl("https://untrusted-host.example/setup.exe")
                .status(DesktopRelease.Status.DRAFT).build();
        when(desktopReleaseRepository.findById(5L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> controller.publish(5L)).isInstanceOf(IllegalArgumentException.class);
        verify(desktopReleaseRepository, never()).save(any());
    }

    @Test
    void withdraw_setsWithdrawnStatus() {
        DesktopRelease published = DesktopRelease.builder()
                .id(9L).version("1.1.0").status(DesktopRelease.Status.PUBLISHED).build();
        when(desktopReleaseRepository.findById(9L)).thenReturn(Optional.of(published));

        controller.withdraw(9L);

        assertThat(published.getStatus()).isEqualTo(DesktopRelease.Status.WITHDRAWN);
        verify(desktopReleaseRepository).save(published);
    }

    @Test
    void update_rejectsEditingAPublishedRelease() {
        DesktopRelease published = DesktopRelease.builder()
                .id(3L).status(DesktopRelease.Status.PUBLISHED).build();
        when(desktopReleaseRepository.findById(3L)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> controller.update(3L, Map.of("version", "1.3.0")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
