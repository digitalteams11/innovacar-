package com.carrental.controller;

import com.carrental.entity.Announcement;
import com.carrental.entity.AnnouncementDismissal;
import com.carrental.entity.Role;
import com.carrental.entity.User;
import com.carrental.repository.AnnouncementDismissalRepository;
import com.carrental.repository.AnnouncementRepository;
import com.carrental.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the desktop-promotion extension to the shared Announcement system:
 * platform targeting (Windows-only banners) and backend-persisted dismissal
 * cooldown, replacing the old sessionStorage-only dismissal.
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementControllerTest {

    @Mock private AnnouncementRepository announcementRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private AnnouncementDismissalRepository announcementDismissalRepository;

    private AnnouncementController controller;

    @BeforeEach
    void setUp() {
        controller = new AnnouncementController(announcementRepository, tenantRepository, announcementDismissalRepository);
        User user = User.builder().id(42L).email("agent@agency.com").role(Role.ADMIN).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Announcement desktopAnnouncement(Long id, Announcement.Platform platform) {
        return Announcement.builder()
                .id(id).title("t").message("m").audience(Announcement.Audience.ALL)
                .priority(Announcement.Priority.NORMAL).active(true)
                .type(Announcement.Type.DESKTOP_AVAILABLE).platform(platform)
                .dismissible(true).cooldownDays(30)
                .build();
    }

    @Test
    void windowsOnlyAnnouncement_hiddenWhenRequestPlatformDoesNotMatch() {
        when(announcementRepository.findActive(any())).thenReturn(
                List.of(desktopAnnouncement(1L, Announcement.Platform.WINDOWS)));
        when(announcementDismissalRepository.findByUserId(42L)).thenReturn(List.of());

        var response = controller.active("MAC");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isEmpty();
    }

    @Test
    void windowsOnlyAnnouncement_shownWhenRequestPlatformMatches() {
        when(announcementRepository.findActive(any())).thenReturn(
                List.of(desktopAnnouncement(1L, Announcement.Platform.WINDOWS)));
        when(announcementDismissalRepository.findByUserId(42L)).thenReturn(List.of());

        var response = controller.active("WINDOWS");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).hasSize(1);
    }

    @Test
    void dismissedWithinCooldown_hiddenFromActiveList() {
        Announcement announcement = desktopAnnouncement(1L, null);
        when(announcementRepository.findActive(any())).thenReturn(List.of(announcement));
        AnnouncementDismissal dismissal = AnnouncementDismissal.builder()
                .announcementId(1L).userId(42L).dismissedAt(LocalDateTime.now().minusDays(2)).build();
        when(announcementDismissalRepository.findByUserId(42L)).thenReturn(List.of(dismissal));

        var response = controller.active(null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).isEmpty();
    }

    @Test
    void dismissedPastCooldown_reappearsInActiveList() {
        Announcement announcement = desktopAnnouncement(1L, null);
        when(announcementRepository.findActive(any())).thenReturn(List.of(announcement));
        AnnouncementDismissal dismissal = AnnouncementDismissal.builder()
                .announcementId(1L).userId(42L).dismissedAt(LocalDateTime.now().minusDays(31)).build();
        when(announcementDismissalRepository.findByUserId(42L)).thenReturn(List.of(dismissal));

        var response = controller.active(null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(data).hasSize(1);
    }

    @Test
    void dismiss_persistsDismissalForCurrentUser() {
        when(announcementDismissalRepository.findByAnnouncementIdAndUserId(1L, 42L)).thenReturn(java.util.Optional.empty());
        when(announcementDismissalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.dismiss(1L);

        assertThat(response.getBody()).containsEntry("success", true);
    }
}
