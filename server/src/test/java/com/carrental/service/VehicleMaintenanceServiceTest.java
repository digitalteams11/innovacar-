package com.carrental.service;

import com.carrental.dto.maintenance.CreateMaintenanceRequest;
import com.carrental.entity.*;
import com.carrental.dto.maintenance.MaintenanceResponse;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createInProgressMovesVehicleToMaintenance() {
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.save(any())).thenAnswer(invocation -> {
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

        assertThat(result.getStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.MAINTENANCE);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void completeReleasesVehicleWhenNoOtherWorkOrderIsOpen() {
        VehicleMaintenance maintenance = VehicleMaintenance.builder()
                .id(20L).tenant(tenant).vehicle(vehicle).title("Oil service")
                .status(MaintenanceStatus.IN_PROGRESS).build();
        vehicle.setStatut(VehicleStatus.MAINTENANCE);
        when(maintenanceRepository.findByIdAndTenantId(20L, 1L)).thenReturn(Optional.of(maintenance));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.findAllByTenantIdAndVehicleIdOrderByCreatedAtDesc(1L, 10L))
                .thenReturn(List.of(maintenance));
        when(maintenanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateStatus(20L, MaintenanceStatus.COMPLETED);

        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(maintenance.getCompletedAt()).isNotNull();
    }
}
