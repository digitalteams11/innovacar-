package com.carrental.controller;

import com.carrental.entity.DesktopDownloadEvent;
import com.carrental.entity.DesktopRelease;
import com.carrental.repository.DesktopDownloadEventRepository;
import com.carrental.repository.DesktopReleaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the public "coming soon vs real release" contract: this endpoint
 * must never 404/500 when nothing is published yet, must never surface a
 * DRAFT/WITHDRAWN row, and its response DTO must never carry anything
 * beyond the documented public fields (no storage credentials).
 */
@ExtendWith(MockitoExtension.class)
class PublicDesktopReleaseControllerTest {

    @Mock private DesktopReleaseRepository desktopReleaseRepository;
    @Mock private DesktopDownloadEventRepository desktopDownloadEventRepository;

    private PublicDesktopReleaseController controller() {
        return new PublicDesktopReleaseController(desktopReleaseRepository, desktopDownloadEventRepository);
    }

    @Test
    void noPublishedRelease_returnsAvailableFalse_notAnError() {
        when(desktopReleaseRepository.findLatestPublished(
                DesktopRelease.Platform.WINDOWS, DesktopRelease.Architecture.X64, DesktopRelease.Channel.STABLE))
                .thenReturn(Optional.empty());

        var response = controller().latest("WINDOWS", "X64", "STABLE");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("available", false);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void publishedRelease_returnedWithFullMetadataAndNoSecrets() {
        DesktopRelease release = DesktopRelease.builder()
                .id(7L)
                .platform(DesktopRelease.Platform.WINDOWS)
                .architecture(DesktopRelease.Architecture.X64)
                .version("1.2.0")
                .semanticVersion("1.2.0")
                .channel(DesktopRelease.Channel.STABLE)
                .status(DesktopRelease.Status.PUBLISHED)
                .fileName("Innovacar-Setup-1.2.0.exe")
                .downloadUrl("https://github.com/innovacar/desktop/releases/download/v1.2.0/setup.exe")
                .fileSizeBytes(98452311L)
                .sha256("a".repeat(64))
                .minimumOs("Windows 10")
                .mandatoryUpdate(false)
                .publishedAt(LocalDateTime.now())
                .releaseNotesEn("Line one\nLine two")
                .releaseNotesFr("Ligne une")
                .releaseNotesAr("سطر واحد")
                .build();
        when(desktopReleaseRepository.findLatestPublished(
                DesktopRelease.Platform.WINDOWS, DesktopRelease.Architecture.X64, DesktopRelease.Channel.STABLE))
                .thenReturn(Optional.of(release));

        var response = controller().latest("WINDOWS", "X64", "STABLE");

        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("available", true);
        assertThat(body).containsEntry("version", "1.2.0");
        assertThat(body).containsEntry("downloadUrl", "https://github.com/innovacar/desktop/releases/download/v1.2.0/setup.exe");
        assertThat(body).containsEntry("sha256", "a".repeat(64));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> notes = (Map<String, List<String>>) body.get("releaseNotes");
        assertThat(notes.get("en")).containsExactly("Line one", "Line two");
        assertThat(notes.get("fr")).containsExactly("Ligne une");
        assertThat(notes.get("ar")).containsExactly("سطر واحد");

        // No storage credentials, tokens, or internal keys ever leave this DTO.
        assertThat(body.keySet()).noneMatch(key -> key.toLowerCase().contains("secret")
                || key.toLowerCase().contains("token")
                || key.toLowerCase().contains("credential")
                || key.toLowerCase().contains("password"));
    }

    @Test
    void invalidPlatformOrArch_returnsAvailableFalse_neverThrows() {
        var response = controller().latest("MACOS", "ARM99", "STABLE");
        assertThat(response.getBody()).containsEntry("available", false);
    }

    @Test
    void recordDownload_persistsPrivacySafeEvent() {
        DesktopRelease release = DesktopRelease.builder()
                .id(7L).platform(DesktopRelease.Platform.WINDOWS).architecture(DesktopRelease.Architecture.X64)
                .version("1.2.0").build();
        when(desktopReleaseRepository.findById(7L)).thenReturn(Optional.of(release));

        controller().recordDownload(Map.of("releaseId", 7, "source", "DESKTOP_PAGE", "status", "STARTED"));

        ArgumentCaptor<DesktopDownloadEvent> captor = ArgumentCaptor.forClass(DesktopDownloadEvent.class);
        verify(desktopDownloadEventRepository).save(captor.capture());
        DesktopDownloadEvent saved = captor.getValue();
        assertThat(saved.getVersion()).isEqualTo("1.2.0");
        assertThat(saved.getSource()).isEqualTo(DesktopDownloadEvent.Source.DESKTOP_PAGE);
        assertThat(saved.getStatus()).isEqualTo(DesktopDownloadEvent.Status.STARTED);
    }
}
