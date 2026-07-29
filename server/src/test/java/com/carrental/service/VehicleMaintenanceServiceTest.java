package com.carrental.service;

import com.carrental.dto.maintenance.CreateMaintenanceRequest;
import com.carrental.entity.*;
import com.carrental.dto.maintenance.MaintenanceResponse;
import com.carrental.exception.MaintenanceValidationException;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleMaintenanceRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleMaintenanceServiceTest {
    @Mock private VehicleMaintenanceRepository maintenanceRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private NotificationService notificationService;
    @InjectMocks private VehicleMaintenanceService service;

    private Tenant tenant;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
        tenant = Tenant.builder().id(1L).name("Agency").email("agency@test.com").build();
        vehicle = Vehicle.builder().id(10L).tenant(tenant).marque("Dacia Duster")
                .prixJour(new BigDecimal("500")).statut(VehicleStatus.AVAILABLE).build();
        User user = User.builder().id(5L).email("admin@test.com").role(Role.ADMIN).tenant(tenant).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSchedulesWorkOrderAndMovesVehicleToMaintenance() {
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.saveAndFlush(vehicle)).thenReturn(vehicle);
        when(maintenanceRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            VehicleMaintenance value = invocation.getArgument(0);
            value.setId(20L);
            return value;
        });

        CreateMaintenanceRequest request = new CreateMaintenanceRequest();
        request.setVehicleId(10L);
        request.setTitle("Oil service");
        request.setScheduledDate(LocalDateTime.of(2026, 6, 15, 14, 53));
        request.setStatus(MaintenanceStatus.IN_PROGRESS);

        MaintenanceResponse result = service.create(request);

        assertThat(result.getStatus()).isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.MAINTENANCE);
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    // Regression coverage for the production bug where every maintenance
    // creation for a RENTED vehicle was rejected unconditionally, even when
    // clearly scheduled for after the rental period — the reported example
    // was a vehicle currently LOUE/RENTED with a maintenance planned for the
    // next day. Planned (future-dated) maintenance must be allowed while
    // rented, and must NOT flip the vehicle's status away from RENTED
    // (the active rental must never be silently affected).
    @Test
    void plannedFutureMaintenanceIsAllowedForRentedVehicleAndDoesNotChangeVehicleStatus() {
        vehicle.setStatut(VehicleStatus.RENTED);
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            VehicleMaintenance value = invocation.getArgument(0);
            value.setId(20L);
            return value;
        });

        CreateMaintenanceRequest request = new CreateMaintenanceRequest();
        request.setVehicleId(10L);
        request.setTitle("Oil service");
        request.setScheduledDate(LocalDateTime.now().plusDays(1));

        MaintenanceResponse result = service.create(request);

        assertThat(result.getStatus()).isEqualTo(MaintenanceStatus.SCHEDULED);
        // The rental must be left untouched — status changes to MAINTENANCE
        // only when work actually starts, never for a future planned order.
        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.RENTED);
        verify(vehicleRepository, never()).saveAndFlush(any());
    }

    @Test
    void immediateMaintenanceIsStillBlockedForRentedVehicle() {
        vehicle.setStatut(VehicleStatus.RENTED);
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));

        CreateMaintenanceRequest request = new CreateMaintenanceRequest();
        request.setVehicleId(10L);
        request.setTitle("Emergency repair");
        request.setScheduledDate(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(MaintenanceValidationException.class)
                .satisfies(ex -> assertThat(((MaintenanceValidationException) ex).getErrorCode())
                        .isEqualTo("VEHICLE_CURRENTLY_RENTED"));
        verify(maintenanceRepository, never()).saveAndFlush(any());
        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.RENTED);
    }

    @Test
    void immediateMaintenanceForAvailableVehicleStillFlipsStatus() {
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.saveAndFlush(vehicle)).thenReturn(vehicle);
        when(maintenanceRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            VehicleMaintenance value = invocation.getArgument(0);
            value.setId(20L);
            return value;
        });

        CreateMaintenanceRequest request = new CreateMaintenanceRequest();
        request.setVehicleId(10L);
        request.setTitle("Oil service");
        request.setScheduledDate(LocalDateTime.now().minusMinutes(1));

        service.create(request);

        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.MAINTENANCE);
        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    @Test
    void creationIsBlockedWhenVehicleAlreadyHasAnInProgressMaintenanceOrder() {
        VehicleMaintenance existing = VehicleMaintenance.builder()
                .id(99L).tenant(tenant).vehicle(vehicle).title("Existing repair")
                .status(MaintenanceStatus.IN_PROGRESS).build();
        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.findFirstByTenantIdAndVehicleIdAndStatusOrderByCreatedAtDesc(
                1L, 10L, MaintenanceStatus.IN_PROGRESS)).thenReturn(Optional.of(existing));

        CreateMaintenanceRequest request = new CreateMaintenanceRequest();
        request.setVehicleId(10L);
        request.setTitle("Another repair");
        request.setScheduledDate(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(MaintenanceValidationException.class)
                .satisfies(ex -> {
                    MaintenanceValidationException mve = (MaintenanceValidationException) ex;
                    assertThat(mve.getErrorCode()).isEqualTo("ACTIVE_MAINTENANCE_EXISTS");
                    assertThat(mve.getDetails()).containsEntry("existingMaintenanceId", 99L);
                });
        verify(maintenanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeReleasesVehicleWhenNoOtherWorkOrderIsOpen() {
        VehicleMaintenance maintenance = VehicleMaintenance.builder()
                .id(20L).tenant(tenant).vehicle(vehicle).title("Oil service")
                .status(MaintenanceStatus.IN_PROGRESS).build();
        vehicle.setStatut(VehicleStatus.MAINTENANCE);
        when(maintenanceRepository.findByIdAndTenantId(20L, 1L)).thenReturn(Optional.of(maintenance));
        when(maintenanceRepository.findVehicleIdById(20L)).thenReturn(10L);
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(1L, 10L))
                .thenReturn(List.of(maintenance));
        when(maintenanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateStatus(20L, MaintenanceStatus.COMPLETED);

        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(maintenance.getCompletedAt()).isNotNull();
    }
}
