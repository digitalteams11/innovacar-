package com.carrental.service;

import com.carrental.dto.search.GlobalSearchData;
import com.carrental.dto.search.SearchResultDto;
import com.carrental.entity.Client;
import com.carrental.entity.Contract;
import com.carrental.entity.Employee;
import com.carrental.entity.Payment;
import com.carrental.entity.Reservation;
import com.carrental.entity.Vehicle;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.EmployeeRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private static final int CATEGORY_LIMIT = 5;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final VehicleRepository vehicleRepository;
    private final ClientRepository clientRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final PaymentRepository paymentRepository;
    private final RolePermissionService rolePermissionService;

    @Transactional(readOnly = true)
    public GlobalSearchData search(String rawQuery, Integer requestedLimit) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("Agency context missing.");
        }

        String query = normalize(rawQuery);
        int limit = clampLimit(requestedLimit);
        if (query.length() < 2) {
            return new GlobalSearchData(query, List.of());
        }

        List<SearchResultDto> results = new ArrayList<>();
        if (can("VEHICLE_VIEW")) results.addAll(searchVehicles(tenantId, query));
        if (can("CLIENT_VIEW")) results.addAll(searchClients(tenantId, query));
        if (can("RESERVATION_VIEW")) results.addAll(searchReservations(tenantId, query));
        if (can("CONTRACT_VIEW")) results.addAll(searchContracts(tenantId, query));
        if (can("EMPLOYEE_VIEW")) results.addAll(searchEmployees(tenantId, query));
        if (can("PAYMENT_VIEW")) results.addAll(searchPayments(tenantId, query));

        List<SearchResultDto> sorted = results.stream()
                .sorted(Comparator.comparingInt(SearchResultDto::score).reversed()
                        .thenComparing(SearchResultDto::type)
                        .thenComparing(SearchResultDto::title))
                .limit(limit)
                .toList();
        return new GlobalSearchData(query, sorted);
    }

    private boolean can(String permission) {
        try {
            return rolePermissionService.has(permission);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private List<SearchResultDto> searchVehicles(Long tenantId, String query) {
        return vehicleRepository.findAllByTenantId(tenantId).stream()
                .map(vehicle -> vehicleResult(vehicle, query))
                .filter(Objects::nonNull)
                .limit(CATEGORY_LIMIT)
                .toList();
    }

    private SearchResultDto vehicleResult(Vehicle vehicle, String query) {
        int score = score(query, vehicle.getMarque(), vehicle.getPlate(), vehicle.getCategory(),
                vehicle.getFuel(), enumName(vehicle.getStatut()));
        if (score == 0) return null;
        String title = firstNonBlank(vehicle.getMarque(), "Vehicle #" + vehicle.getId());
        String status = enumName(vehicle.getStatut());
        String subtitle = joinParts("Plate: " + blankFallback(vehicle.getPlate(), "-"), status);
        return result("vehicle", "VEHICLE", vehicle.getId(), title, subtitle, status, "/vehicles", "car", score);
    }

    private List<SearchResultDto> searchClients(Long tenantId, String query) {
        return clientRepository.findAllByTenantIdAndDeletedFalse(tenantId).stream()
                .map(client -> clientResult(client, query))
                .filter(Objects::nonNull)
                .limit(CATEGORY_LIMIT)
                .toList();
    }

    private SearchResultDto clientResult(Client client, String query) {
        int score = score(query, client.getName(), client.getPhone(), client.getEmail(),
                client.getCin(), client.getPassportNumber());
        if (score == 0) return null;
        String title = firstNonBlank(client.getName(), "Client #" + client.getId());
        String subtitle = joinParts(client.getPhone(), client.getEmail(), client.getCin());
        return result("client", "CLIENT", client.getId(), title, subtitle, null, "/clients", "user", score);
    }

    private List<SearchResultDto> searchReservations(Long tenantId, String query) {
        return reservationRepository.findAllByTenantId(tenantId).stream()
                .map(reservation -> reservationResult(reservation, query))
                .filter(Objects::nonNull)
                .limit(CATEGORY_LIMIT)
                .toList();
    }

    private SearchResultDto reservationResult(Reservation reservation, String query) {
        String number = "RSV-" + reservation.getId();
        String clientName = reservation.getClient() == null ? null : reservation.getClient().getName();
        String vehicleName = reservation.getVehicle() == null ? null : reservation.getVehicle().getMarque();
        String status = enumName(reservation.getStatus());
        String date = reservation.getDateStart() == null ? null : reservation.getDateStart().format(DATE_FORMAT);
        int score = score(query, number, clientName, vehicleName, status, date);
        if (score == 0) return null;
        String subtitle = joinParts(clientName, vehicleName, date);
        return result("reservation", "RESERVATION", reservation.getId(), number, subtitle, status, "/reservations", "calendar", score);
    }

    private List<SearchResultDto> searchContracts(Long tenantId, String query) {
        return contractRepository.findAllByTenantId(tenantId).stream()
                .map(contract -> contractResult(contract, query))
                .filter(Objects::nonNull)
                .limit(CATEGORY_LIMIT)
                .toList();
    }

    private SearchResultDto contractResult(Contract contract, String query) {
        String clientName = firstNonBlank(contract.getClientName(), contract.getClientFullName(),
                contract.getClient() == null ? null : contract.getClient().getName());
        String vehicleName = firstNonBlank(contract.getVehicleBrand(), contract.getVehicleModel(),
                contract.getVehicle() == null ? null : contract.getVehicle().getMarque());
        String status = enumName(contract.getStatus());
        int score = score(query, contract.getContractNumber(), clientName, vehicleName, status);
        if (score == 0) return null;
        String title = firstNonBlank(contract.getContractNumber(), "Contract #" + contract.getId());
        String subtitle = joinParts(clientName, vehicleName, status);
        return result("contract", "CONTRACT", contract.getId(), title, subtitle, status, "/contracts/" + contract.getId(), "file-text", score);
    }

    private List<SearchResultDto> searchEmployees(Long tenantId, String query) {
        return employeeRepository.findAllByTenantIdAndDeletedFalse(tenantId).stream()
                .map(employee -> employeeResult(employee, query))
                .filter(Objects::nonNull)
                .limit(CATEGORY_LIMIT)
                .toList();
    }

    private SearchResultDto employeeResult(Employee employee, String query) {
        String status = enumName(employee.getStatus());
        int score = score(query, employee.getName(), employee.getEmail(), employee.getPhone(),
                employee.getRole(), employee.getDepartment(), status);
        if (score == 0) return null;
        String title = firstNonBlank(employee.getName(), "Employee #" + employee.getId());
        String subtitle = joinParts(employee.getRole(), employee.getDepartment(), status);
        return result("employee", "EMPLOYEE", employee.getId(), title, subtitle, status, "/employees", "users", score);
    }

    private List<SearchResultDto> searchPayments(Long tenantId, String query) {
        return paymentRepository.findAllByTenantIdOrderByPaymentDateDesc(tenantId).stream()
                .map(payment -> paymentResult(payment, query))
                .filter(Objects::nonNull)
                .limit(CATEGORY_LIMIT)
                .toList();
    }

    private SearchResultDto paymentResult(Payment payment, String query) {
        String amount = formatAmount(payment.getAmount());
        String status = enumName(payment.getStatus());
        String contractNumber = payment.getContract() == null ? null : payment.getContract().getContractNumber();
        int score = score(query, payment.getPaymentNumber(), payment.getReference(), amount, status, contractNumber);
        if (score == 0) return null;
        String title = firstNonBlank(payment.getPaymentNumber(), "Payment " + amount);
        String subtitle = joinParts(contractNumber, amount, status);
        return result("payment", "PAYMENT", payment.getId(), title, subtitle, status, "/payments", "credit-card", score);
    }

    private SearchResultDto result(String idPrefix, String type, Long entityId, String title, String subtitle,
                                   String status, String route, String icon, int score) {
        return new SearchResultDto(
                idPrefix + "-" + entityId,
                type,
                entityId,
                title,
                blankFallback(subtitle, ""),
                status,
                route,
                icon,
                score
        );
    }

    private int score(String query, String... values) {
        int best = 0;
        for (String value : values) {
            if (isBlank(value)) continue;
            String normalized = normalize(value);
            if (normalized.equals(query)) best = Math.max(best, 100);
            else if (normalized.startsWith(query)) best = Math.max(best, 92);
            else if (normalized.contains(query)) best = Math.max(best, 76);
        }
        return best;
    }

    private int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null) return DEFAULT_LIMIT;
        return Math.max(1, Math.min(MAX_LIMIT, requestedLimit));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) return null;
        return value.stripTrailingZeros().toPlainString() + " MAD";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return null;
    }

    private String blankFallback(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String joinParts(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) parts.add(value);
        }
        return String.join(" · ", parts);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
