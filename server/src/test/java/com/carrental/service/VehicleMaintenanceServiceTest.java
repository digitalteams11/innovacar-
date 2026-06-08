package com.carrental.service;

import com.carrental.entity.*;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleMaintenanceServiceTest {
    @Mock private VehicleMaintenanceRepository maintenanceRepository;
    @Mock private VehicleRepository vehicleRepository;
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

        Map<String, Object> result = service.create(Map.of(
                "vehicleId", 10,
                "title", "Oil service",
                "status", "IN_PROGRESS"
        ));

        assertThat(result.get("status")).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void completeReleasesVehicleWhenNoOtherWorkOrderIsOpen() {
        VehicleMaintenance maintenance = VehicleMaintenance.builder()
                .id(20L).tenant(tenant).vehicle(vehicle).title("Oil service")
                .status(MaintenanceStatus.IN_PROGRESS).build();
        vehicle.setStatut(VehicleStatus.IN_MAINTENANCE);
        when(maintenanceRepository.findByIdAndTenantId(20L, 1L)).thenReturn(Optional.of(maintenance));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(10L, 1L)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.existsByTenantIdAndVehicleIdAndStatusIn(eq(1L), eq(10L), anyCollection()))
                .thenReturn(false);
        when(maintenanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateStatus(20L, MaintenanceStatus.COMPLETED);

        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(maintenance.getCompletedAt()).isNotNull();
    }
}
