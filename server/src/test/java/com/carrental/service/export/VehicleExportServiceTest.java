package com.carrental.service.export;

import com.carrental.entity.MaintenanceStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.VehicleMaintenanceRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleExportServiceTest {

    private static final Long TENANT_ID = 1L;

    @Mock private VehicleRepository vehicleRepository;
    @Mock private VehicleMaintenanceRepository maintenanceRepository;

    private VehicleExportService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);
        service = new VehicleExportService(vehicleRepository, maintenanceRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Vehicle vehicle(Long id, VehicleStatus status, String category) {
        return Vehicle.builder().id(id).marque("Toyota Corolla").brand("Toyota").model("Corolla")
                .statut(status).category(category).prixJour(new BigDecimal("199.00")).tenant(null).build();
    }

    @Test
    void resolveRows_filtersByStatusAndCategory() {
        when(vehicleRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(
                vehicle(1L, VehicleStatus.AVAILABLE, "SUV"),
                vehicle(2L, VehicleStatus.RENTED, "SUV"),
                vehicle(3L, VehicleStatus.AVAILABLE, "SEDAN")
        ));
        when(maintenanceRepository.findAllByTenantIdAndVehicleIdInAndStatus(any(), any(), any())).thenReturn(List.of());

        var rows = service.resolveRows(new VehicleExportService.Filters(
                VehicleStatus.AVAILABLE, "SUV", null, null, false, null, null, null), 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void resolveRows_noMatches_throwsExportNoData() {
        when(vehicleRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(
                vehicle(1L, VehicleStatus.RENTED, "SUV")));

        assertThatThrownBy(() -> service.resolveRows(new VehicleExportService.Filters(
                VehicleStatus.AVAILABLE, null, null, null, false, null, null, null), 100))
                .isInstanceOf(VehicleExportService.ExportNoDataException.class);
    }

    @Test
    void resolveRows_tooManyRows_throwsExportTooLarge() {
        when(vehicleRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(
                vehicle(1L, VehicleStatus.AVAILABLE, "SUV"),
                vehicle(2L, VehicleStatus.AVAILABLE, "SUV")));

        assertThatThrownBy(() -> service.resolveRows(new VehicleExportService.Filters(
                null, null, null, null, false, null, null, null), 1))
                .isInstanceOf(VehicleExportService.ExportTooLargeException.class);
    }

    @Test
    void resolveRows_archivedExcludedByDefault() {
        when(vehicleRepository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(
                vehicle(1L, VehicleStatus.ARCHIVED, "SUV"),
                vehicle(2L, VehicleStatus.AVAILABLE, "SUV")));
        when(maintenanceRepository.findAllByTenantIdAndVehicleIdInAndStatus(any(), any(), any())).thenReturn(List.of());

        var rows = service.resolveRows(new VehicleExportService.Filters(
                null, null, null, null, false, null, null, null), 100);

        assertThat(rows).extracting("id").containsExactly(2L);
    }

    @Test
    void exportRow_hasNoClientIdentityFields() {
        // Structural guard: VehicleExportRow must never grow a document/CIN/
        // passport field — fleet exports are vehicle-only data by design
        // (see Track A's ClientIdentityDocument, which must stay separate).
        List<String> fieldNames = java.util.Arrays.stream(com.carrental.dto.export.VehicleExportRow.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .map(String::toLowerCase)
                .toList();

        assertThat(fieldNames).noneMatch(name ->
                name.contains("document") || name.contains("cin") || name.contains("passport") || name.contains("client"));
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
