package com.carrental.service.export;

import com.carrental.dto.export.VehicleExportRow;
import com.carrental.entity.MaintenanceStatus;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.VehicleMaintenanceRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves filters and assembles {@link VehicleExportRow}s for the fleet
 * export endpoints. Deliberately vehicle-only — client identity documents
 * are never joined in here (see ClientIdentityDocument / Track A).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleExportService {

    /** Both PDF and XLSX are memory-resident formats regardless of streaming, so both share this cap. */
    public static final int MAX_ROWS_PDF_XLSX = 5000;
    public static final int MAX_ROWS_CSV = 50000;

    public static class ExportTooLargeException extends RuntimeException {
        public ExportTooLargeException(String message) { super(message); }
    }

    public static class ExportNoDataException extends RuntimeException {
        public ExportNoDataException(String message) { super(message); }
    }

    private final VehicleRepository vehicleRepository;
    private final VehicleMaintenanceRepository maintenanceRepository;

    public record Filters(
            VehicleStatus status,
            String category,
            Long branchId,
            String search,
            boolean includeArchived,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<Long> ids
    ) {}

    @Transactional(readOnly = true)
    public List<VehicleExportRow> resolveRows(Filters filters, int maxRows) {
        Long tenantId = TenantContext.getCurrentTenantId();
        List<Vehicle> vehicles = vehicleRepository.findAllByTenantId(tenantId);

        List<Vehicle> filtered = vehicles.stream()
                .filter(v -> filters.status() == null || v.getStatut() == filters.status())
                .filter(v -> !StringUtils.hasText(filters.category()) || filters.category().equalsIgnoreCase(v.getCategory()))
                .filter(v -> filters.branchId() == null || (v.getBranch() != null && filters.branchId().equals(v.getBranch().getId())))
                .filter(v -> filters.ids() == null || filters.ids().isEmpty() || filters.ids().contains(v.getId()))
                .filter(v -> filters.includeArchived() || v.getStatut() != VehicleStatus.ARCHIVED)
                .filter(v -> !StringUtils.hasText(filters.search()) || matchesSearch(v, filters.search()))
                .toList();

        if (filtered.isEmpty()) {
            throw new ExportNoDataException("No vehicles match the selected filters.");
        }
        if (filtered.size() > maxRows) {
            throw new ExportTooLargeException(
                    "This export would contain " + filtered.size() + " vehicles, which exceeds the " + maxRows
                            + "-row limit for this format. Narrow your filters and try again.");
        }

        Map<Long, LocalDate> nextMaintenanceByVehicle = nextMaintenanceDates(tenantId,
                filtered.stream().map(Vehicle::getId).toList());

        return filtered.stream()
                .map(v -> toRow(v, nextMaintenanceByVehicle.get(v.getId())))
                .toList();
    }

    private boolean matchesSearch(Vehicle v, String search) {
        String q = search.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(v.getMarque(), q) || containsIgnoreCase(v.getBrand(), q)
                || containsIgnoreCase(v.getModel(), q) || containsIgnoreCase(v.getPlate(), q);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private Map<Long, LocalDate> nextMaintenanceDates(Long tenantId, List<Long> vehicleIds) {
        if (vehicleIds.isEmpty()) return Map.of();
        return maintenanceRepository.findAllByTenantIdAndVehicleIdInAndStatus(tenantId, vehicleIds, MaintenanceStatus.SCHEDULED)
                .stream()
                .filter(m -> m.getScheduledAt() != null && m.getVehicle() != null)
                .collect(Collectors.toMap(
                        m -> m.getVehicle().getId(),
                        m -> m.getScheduledAt().toLocalDate(),
                        (a, b) -> a.isBefore(b) ? a : b));
    }

    private VehicleExportRow toRow(Vehicle v, LocalDate nextMaintenance) {
        String brand = StringUtils.hasText(v.getBrand()) ? v.getBrand() : v.getMarque();
        String model = v.getModel();
        return VehicleExportRow.builder()
                .id(v.getId())
                .brand(brand)
                .model(model)
                .category(v.getCategory())
                .plate(v.getPlate())
                .status(v.getStatut() != null ? v.getStatut().name() : null)
                .pricePerDay(v.getPrixJour())
                .fuel(v.getFuel())
                .transmission(v.getTransmission())
                .seats(v.getSeatCount())
                .mileage(v.getMileageCurrent())
                .branch(v.getBranch() != null ? v.getBranch().getName() : null)
                .nextMaintenanceDate(nextMaintenance)
                .depositAmount(v.getDepositAmount())
                .weeklyPrice(v.getPrixSemaine())
                .monthlyPrice(v.getPrixMois())
                .gpsStatus(v.getGpsStatus() != null ? v.getGpsStatus().name() : null)
                .gpsEnabled(v.getGpsEnabled())
                .build();
    }
}
