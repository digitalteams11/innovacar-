package com.carrental.service;

import com.carrental.dto.inspection.InspectionResponse;
import com.carrental.entity.Client;
import com.carrental.entity.Inspection;
import com.carrental.entity.InspectionMedia;
import com.carrental.entity.InspectionMediaType;
import com.carrental.entity.InspectionStatus;
import com.carrental.entity.InspectionType;
import com.carrental.entity.Tenant;
import com.carrental.entity.Vehicle;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.InspectionMediaRepository;
import com.carrental.repository.InspectionRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.TenantSettingsRepository;
import com.carrental.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {
    @Mock private InspectionRepository inspectionRepository;
    @Mock private InspectionMediaRepository mediaRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantSettingsRepository tenantSettingsRepository;

    @InjectMocks private InspectionService service;

    @TempDir Path tempDir;

    @Test
    void uploadUsesValidatedContentTypeForStoredExtension() {
        Tenant tenant = Tenant.builder().id(1L).name("Agency").email("agency@test.com").build();
        Client client = Client.builder().id(2L).tenant(tenant).name("Client").build();
        Vehicle vehicle = Vehicle.builder().id(3L).tenant(tenant).marque("Dacia Duster").plate("123-A-45").build();
        Inspection inspection = Inspection.builder()
                .id(10L)
                .tenant(tenant)
                .client(client)
                .vehicle(vehicle)
                .type(InspectionType.BEFORE_DELIVERY)
                .status(InspectionStatus.NOT_STARTED)
                .token("valid-token")
                .tokenExpiresAt(LocalDateTime.now().plusHours(1))
                .mediaExpiresAt(LocalDateTime.now().plusDays(7))
                .media(new ArrayList<>())
                .build();

        ReflectionTestUtils.setField(service, "storageDir", tempDir.toString());
        when(inspectionRepository.findByToken("valid-token")).thenReturn(Optional.of(inspection));
        when(mediaRepository.save(any(InspectionMedia.class))).thenAnswer(invocation -> {
            InspectionMedia media = invocation.getArgument(0);
            if (media.getId() == null) media.setId(99L);
            if (media.getActive() == null) media.setActive(true);
            return media;
        });
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "front.jpg.exe",
                "image/jpeg",
                new byte[] {1, 2, 3});

        InspectionResponse response = service.upload(10L, "valid-token", file,
                "front", null, InspectionMediaType.PHOTO, null);

        ArgumentCaptor<InspectionMedia> mediaCaptor = ArgumentCaptor.forClass(InspectionMedia.class);
        org.mockito.Mockito.verify(mediaRepository, org.mockito.Mockito.atLeastOnce()).save(mediaCaptor.capture());
        InspectionMedia savedMedia = mediaCaptor.getAllValues().get(0);

        assertThat(savedMedia.getStoragePath()).endsWith(".jpg");
        assertThat(savedMedia.getStoragePath()).doesNotEndWith(".exe");
        assertThat(response.getMedia()).hasSize(1);
    }
}
