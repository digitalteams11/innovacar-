package com.carrental.service;

import com.carrental.dto.vehicle.CreateVehicleRequest;
import com.carrental.dto.vehicle.UpdateVehicleRequest;
import com.carrental.dto.vehicle.VehicleResponse;
import com.carrental.entity.Role;
import com.carrental.entity.Tenant;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.BranchRepository;
import com.carrental.repository.TenantRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VehicleService}.
 * Uses Mockito — no Spring context loaded.
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private TenantRepository  tenantRepository;
    @Mock private BranchRepository branchRepository;

    @InjectMocks
    private VehicleService vehicleService;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static final Long TENANT_ID  = 1L;
    private static final Long VEHICLE_ID = 100L;

    private Tenant  tenant;
    private Vehicle availableVehicle;
    private Vehicle rentedVehicle;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);

        tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Fleet Corp")
                .email("billing@fleet.com")
                .subscriptionActive(true)
                .subscriptionEndDate(LocalDate.now().plusYears(1))
                .build();

        availableVehicle = Vehicle.builder()
                .id(VEHICLE_ID)
                .marque("Toyota Corolla 2023")
                .prixJour(new BigDecimal("89.99"))
                .statut(VehicleStatus.AVAILABLE)
                .tenant(tenant)
                .build();

        rentedVehicle = Vehicle.builder()
                .id(200L)
                .marque("BMW Series 3")
                .prixJour(new BigDecimal("149.00"))
                .statut(VehicleStatus.RENTED)
                .tenant(tenant)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── getAllVehicles ────────────────────────────────────────────────────────

    @Test
    void getAllVehicles_noFilter_returnsAll() {
        when(vehicleRepository.findAllByTenantId(TENANT_ID))
                .thenReturn(List.of(availableVehicle, rentedVehicle));

        List<VehicleResponse> result = vehicleService.getAllVehicles(null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(VehicleResponse::getMarque)
                .containsExactlyInAnyOrder("Toyota Corolla 2023", "BMW Series 3");
    }

    @Test
    void getAllVehicles_withStatusFilter_delegatesToCorrectQuery() {
        when(vehicleRepository.findAllByTenantIdAndStatut(TENANT_ID, VehicleStatus.AVAILABLE))
                .thenReturn(List.of(availableVehicle));

        List<VehicleResponse> result = vehicleService.getAllVehicles(VehicleStatus.AVAILABLE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatut()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(vehicleRepository, never()).findAllByTenantId(any());
    }

    // ── getVehicleById ───────────────────────────────────────────────────────

    @Test
    void getVehicleById_returnsVehicle_whenExists() {
        when(vehicleRepository.findByIdAndTenantId(VEHICLE_ID, TENANT_ID))
                .thenReturn(Optional.of(availableVehicle));

        VehicleResponse response = vehicleService.getVehicleById(VEHICLE_ID);

        assertThat(response.getId()).isEqualTo(VEHICLE_ID);
        assertThat(response.getPrixJour()).isEqualByComparingTo("89.99");
    }

    @Test
    void getVehicleById_throws404_whenNotInTenant() {
        when(vehicleRepository.findByIdAndTenantId(999L, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.getVehicleById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── createVehicle ─────────────────────────────────────────────────────────

    @Test
    void createVehicle_persistsWithDefaultStatut() {
        CreateVehicleRequest req = new CreateVehicleRequest();
        req.setMarque("Renault Clio");
        req.setPrixJour(new BigDecimal("55.00"));
        // statut intentionally omitted → should default to AVAILABLE

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        Vehicle saved = Vehicle.builder()
                .id(300L).marque("Renault Clio")
                .prixJour(new BigDecimal("55.00"))
                .statut(VehicleStatus.AVAILABLE).tenant(tenant).build();
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(saved);

        VehicleResponse response = vehicleService.createVehicle(req);

        assertThat(response.getStatut()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(response.getMarque()).isEqualTo("Renault Clio");
        verify(vehicleRepository).save(argThat(v -> v.getStatut() == VehicleStatus.AVAILABLE));
    }

    @Test
    void createVehicle_respectsExplicitStatut() {
        CreateVehicleRequest req = new CreateVehicleRequest();
        req.setMarque("Dacia Sandero");
        req.setPrixJour(new BigDecimal("40.00"));
        req.setStatut(VehicleStatus.MAINTENANCE);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        Vehicle saved = Vehicle.builder()
                .id(301L).marque("Dacia Sandero")
                .prixJour(new BigDecimal("40.00"))
                .statut(VehicleStatus.MAINTENANCE).tenant(tenant).build();
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(saved);

        VehicleResponse response = vehicleService.createVehicle(req);

        assertThat(response.getStatut()).isEqualTo(VehicleStatus.MAINTENANCE);
    }

    // ── updateVehicle ─────────────────────────────────────────────────────────

    @Test
    void updateVehicle_appliesOnlyNonNullFields() {
        UpdateVehicleRequest req = new UpdateVehicleRequest();
        req.setStatut(VehicleStatus.RENTED); // only statut changes

        when(vehicleRepository.findByIdAndTenantId(VEHICLE_ID, TENANT_ID))
                .thenReturn(Optional.of(availableVehicle));

        Vehicle saved = Vehicle.builder()
                .id(VEHICLE_ID).marque("Toyota Corolla 2023")
                .prixJour(new BigDecimal("89.99"))
                .statut(VehicleStatus.RENTED).tenant(tenant).build();
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(saved);

        VehicleResponse response = vehicleService.updateVehicle(VEHICLE_ID, req);

        assertThat(response.getStatut()).isEqualTo(VehicleStatus.RENTED);
        assertThat(response.getMarque()).isEqualTo("Toyota Corolla 2023"); // unchanged
    }

    // ── deleteVehicle ─────────────────────────────────────────────────────────

    @Test
    void deleteVehicle_removesAvailableVehicle() {
        when(vehicleRepository.findByIdAndTenantId(VEHICLE_ID, TENANT_ID))
                .thenReturn(Optional.of(availableVehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        vehicleService.deleteVehicle(VEHICLE_ID);

        verify(vehicleRepository).save(availableVehicle);
        assertThat(availableVehicle.getDeleted()).isTrue();
        assertThat(availableVehicle.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteVehicle_throwsWhenRented() {
        when(vehicleRepository.findByIdAndTenantId(200L, TENANT_ID))
                .thenReturn(Optional.of(rentedVehicle));

        assertThatThrownBy(() -> vehicleService.deleteVehicle(200L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RENTED");
    }

    // ── countAvailable ────────────────────────────────────────────────────────

    @Test
    void countAvailable_returnsCorrectCount() {
        when(vehicleRepository.countByTenantIdAndStatut(TENANT_ID, VehicleStatus.AVAILABLE))
                .thenReturn(7L);

        long count = vehicleService.countAvailable();

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void vehicleResponseUsesSafeDefaultsForSparseRows() {
        Vehicle sparse = Vehicle.builder()
                .id(400L)
                .tenant(tenant)
                .build();

        VehicleResponse response = VehicleResponse.from(sparse);

        assertThat(response.getMarque()).isEmpty();
        assertThat(response.getPrixJour()).isEqualByComparingTo("0.00");
        assertThat(response.getStatut()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(response.getCategory()).isEmpty();
        assertThat(response.getGpsEnabled()).isFalse();
    }
}
