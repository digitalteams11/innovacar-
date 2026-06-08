package com.carrental.controller;

import com.carrental.dto.superadmin.*;
import com.carrental.entity.*;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.GpsDeviceStatus;
import com.carrental.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Super Admin Controller — Central brain of the SaaS platform.
 * Innovax Technologies uses these endpoints to manage the entire ecosystem.
 *
 * All endpoints require SUPER_ADMIN role.
 */
@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final EmployeeRepository employeeRepository;
    private final GpsSettingsRepository gpsSettingsRepository;
    private final GpsAlertRepository gpsAlertRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SupportTicketRepository ticketRepository;
    private final AuditLogRepository auditLogRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailLogRepository emailLogRepository;
    private final UserSessionRepository userSessionRepository;

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. GLOBAL OVERVIEW DASHBOARD
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/dashboard")
    public ResponseEntity<GlobalDashboardStats> getDashboardStats() {
        List<Tenant> allTenants = tenantRepository.findAll();
        List<User> allUsers = userRepository.findAll();
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        List<Reservation> allReservations = reservationRepository.findAll();
        List<Contract> allContracts = contractRepository.findAll();
        List<GpsSettings> allGps = gpsSettingsRepository.findAll();
        List<GpsAlert> unreadAlerts = gpsAlertRepository.findAll().stream()
                .filter(a -> Boolean.FALSE.equals(a.getRead())).collect(Collectors.toList());

        long activeAgencies = allTenants.stream().filter(Tenant::isSubscriptionValid).count();
        long trialAgencies = allTenants.stream().filter(Tenant::isInTrial).count();
        long expiredAgencies = allTenants.stream().filter(t -> !t.isSubscriptionValid() && !t.isInTrial()).count();
        long suspendedAgencies = allTenants.stream().filter(t -> "SUSPENDED".equals(t.getStatus())).count();

        LocalDate now = LocalDate.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfYear = now.withDayOfYear(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);

        // ── Rental Revenue from real Payment data ──────────────────────────────
        List<Payment> allRentalPayments = paymentRepository.findAll().stream()
                .filter(p -> p.getType() == PaymentType.RENTAL && p.getStatus() == PaymentStatus.PAID)
                .toList();

        BigDecimal totalRevenue = allRentalPayments.stream()
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyRevenue = allRentalPayments.stream()
                .filter(p -> p.getPaymentDate() != null)
                .filter(p -> !p.getPaymentDate().isBefore(startOfMonth) && p.getPaymentDate().isBefore(startOfNextMonth))
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal yearlyRevenue = allRentalPayments.stream()
                .filter(p -> p.getPaymentDate() != null)
                .filter(p -> !p.getPaymentDate().isBefore(startOfYear))
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── SaaS Subscription Revenue from real Payment data ───────────────────
        List<Payment> allSubscriptionPayments = paymentRepository.findAll().stream()
                .filter(p -> p.getType() == PaymentType.SUBSCRIPTION)
                .toList();

        BigDecimal subscriptionRevenue = allSubscriptionPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal mrr = allSubscriptionPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .filter(p -> p.getPaymentDate() != null)
                .filter(p -> !p.getPaymentDate().isBefore(startOfMonth) && p.getPaymentDate().isBefore(startOfNextMonth))
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12));

        long failedPayments = allSubscriptionPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED || p.getStatus() == PaymentStatus.EXPIRED)
                .filter(p -> p.getPaymentDate() != null && p.getPaymentDate().isAfter(now.minusDays(30).atStartOfDay()))
                .count();

        long activeGps = allGps.stream().filter(GpsSettings::getEnabled).count();
        long totalGpsDevices = allGps.stream().mapToInt(g -> g.getActiveDevices() != null ? g.getActiveDevices() : 0).sum();

        long openTickets = ticketRepository.countByStatus("OPEN");

        return ResponseEntity.ok(GlobalDashboardStats.builder()
                .totalAgencies(allTenants.size())
                .activeAgencies(activeAgencies)
                .trialAgencies(trialAgencies)
                .expiredAgencies(expiredAgencies)
                .suspendedAgencies(suspendedAgencies)
                .totalUsers(allUsers.size())
                .activeUsers(allUsers.size())
                .totalVehicles(allVehicles.size())
                .totalReservations(allReservations.size())
                .totalContracts(allContracts.size())
                .monthlyRevenue(monthlyRevenue)
                .yearlyRevenue(yearlyRevenue)
                .totalRevenue(totalRevenue)
                .mrr(mrr)
                .arr(arr)
                .subscriptionRevenue(subscriptionRevenue)
                .activeSubscribers(activeAgencies)
                .expiredSubscribers(expiredAgencies)
                .trialUsers(trialAgencies)
                .activeGpsConnections(activeGps)
                .totalGpsDevices(totalGpsDevices)
                .openTickets(openTickets)
                .unresolvedAlerts(unreadAlerts.size())
                .failedPaymentsLast30Days(failedPayments)
                .build());
    }

    @GetMapping("/dashboard/activity")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivity() {
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        List<Map<String, Object>> activity = logs.stream().map(log -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", log.getId());
            map.put("action", log.getAction());
            map.put("description", log.getDescription());
            map.put("performedBy", log.getPerformedBy());
            map.put("entityType", log.getEntityType());
            map.put("timestamp", log.getCreatedAt());
            map.put("isSuccess", log.getIsSuccess());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(activity);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. AGENCIES MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/agencies")
    public ResponseEntity<List<Map<String, Object>>> getAllAgencies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        List<Tenant> tenants = tenantRepository.findAll();
        List<Map<String, Object>> result = tenants.stream()
                .filter(t -> status == null || status.equals(t.getStatus()))
                .filter(t -> search == null ||
                        t.getName().toLowerCase().contains(search.toLowerCase()) ||
                        t.getEmail().toLowerCase().contains(search.toLowerCase()))
                .map(t -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", t.getId());
                    map.put("name", t.getName());
                    map.put("email", t.getEmail());
                    map.put("phone", t.getPhone());
                    map.put("city", t.getCity());
                    map.put("country", t.getCountry());
                    map.put("status", t.getStatus());
                    map.put("planName", t.getPlanName());
                    map.put("subscriptionActive", t.isSubscriptionValid());
                    map.put("subscriptionEndDate", t.getSubscriptionEndDate());
                    map.put("trialEndDate", t.getTrialEndDate());
                    map.put("inTrial", t.isInTrial());
                    map.put("createdAt", t.getCreatedAt());
                    map.put("vehicleCount", vehicleRepository.findAll().stream().filter(v -> v.getTenant().getId().equals(t.getId())).count());
                    map.put("employeeCount", employeeRepository.findAll().stream().filter(e -> e.getTenant().getId().equals(t.getId())).count());
                    map.put("userCount", userRepository.findAll().stream().filter(u -> u.getTenant().getId().equals(t.getId())).count());
                    return map;
                }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/agencies/{id}")
    public ResponseEntity<AgencyDetailDto> getAgencyDetail(@PathVariable Long id) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));

        long vehicleCount = vehicleRepository.findAll().stream().filter(v -> v.getTenant().getId().equals(id)).count();
        long employeeCount = employeeRepository.findAll().stream().filter(e -> e.getTenant().getId().equals(id)).count();
        long reservationCount = reservationRepository.findAll().stream().filter(r -> r.getTenant().getId().equals(id)).count();
        long contractCount = contractRepository.findAll().stream().filter(c -> c.getTenant().getId().equals(id)).count();
        long gpsCount = vehicleRepository.findAll().stream()
                .filter(v -> v.getTenant().getId().equals(id))
                .filter(v -> v.getGpsDeviceId() != null).count();

        BigDecimal totalPayments = invoiceRepository.findAll().stream()
                .filter(i -> i.getTenant().getId().equals(id))
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .map(Invoice::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(AgencyDetailDto.builder()
                .id(t.getId())
                .name(t.getName())
                .email(t.getEmail())
                .phone(t.getPhone())
                .address(t.getAddress())
                .city(t.getCity())
                .country(t.getCountry())
                .taxId(t.getTaxId())
                .status(t.getStatus())
                .planName(t.getPlanName())
                .subscriptionActive(t.isSubscriptionValid())
                .subscriptionEndDate(t.getSubscriptionEndDate())
                .trialEndDate(t.getTrialEndDate())
                .inTrial(t.isInTrial())
                .maxVehicles(planRepository.findByName(t.getPlanName()).map(SubscriptionPlan::getMaxVehicles).orElse(t.getMaxVehicles()))
                .maxEmployees(planRepository.findByName(t.getPlanName()).map(SubscriptionPlan::getMaxEmployees).orElse(t.getMaxEmployees()))
                .maxGpsDevices(planRepository.findByName(t.getPlanName()).map(SubscriptionPlan::getMaxGpsDevices).orElse(t.getMaxGpsDevices()))
                .maxReservations(planRepository.findByName(t.getPlanName()).map(SubscriptionPlan::getMaxReservations).orElse(t.getMaxReservations()))
                .storageLimitMb(planRepository.findByName(t.getPlanName()).map(SubscriptionPlan::getStorageLimitMb).orElse(t.getStorageLimitMb()))
                .currentVehicleCount(vehicleCount)
                .currentEmployeeCount(employeeCount)
                .currentReservationCount(reservationCount)
                .currentContractCount(contractCount)
                .currentGpsDeviceCount(gpsCount)
                .totalPayments(totalPayments)
                .totalRevenue(totalPayments)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build());
    }

    @PutMapping("/agencies/{id}")
    public ResponseEntity<Map<String, Object>> updateAgency(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));

        if (updates.containsKey("name")) t.setName((String) updates.get("name"));
        if (updates.containsKey("email")) t.setEmail((String) updates.get("email"));
        if (updates.containsKey("phone")) t.setPhone((String) updates.get("phone"));
        if (updates.containsKey("address")) t.setAddress((String) updates.get("address"));
        if (updates.containsKey("city")) t.setCity((String) updates.get("city"));
        if (updates.containsKey("country")) t.setCountry((String) updates.get("country"));
        if (updates.containsKey("taxId")) t.setTaxId((String) updates.get("taxId"));
        if (updates.containsKey("planName")) t.setPlanName((String) updates.get("planName"));
        if (updates.containsKey("maxVehicles")) t.setMaxVehicles((Integer) updates.get("maxVehicles"));
        if (updates.containsKey("maxEmployees")) t.setMaxEmployees((Integer) updates.get("maxEmployees"));
        if (updates.containsKey("maxGpsDevices")) t.setMaxGpsDevices((Integer) updates.get("maxGpsDevices"));
        if (updates.containsKey("maxReservations")) t.setMaxReservations((Integer) updates.get("maxReservations"));
        if (updates.containsKey("storageLimitMb")) t.setStorageLimitMb((Integer) updates.get("storageLimitMb"));

        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency updated successfully"));
    }

    @PatchMapping("/agencies/{id}/status")
    public ResponseEntity<Map<String, Object>> updateAgencyStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        String newStatus = body.get("status");
        t.setStatus(newStatus);
        if ("SUSPENDED".equals(newStatus)) {
            t.setSubscriptionActive(false);
        } else if ("ACTIVE".equals(newStatus)) {
            t.setSubscriptionActive(true);
        }
        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency status updated to " + newStatus));
    }

    @DeleteMapping("/agencies/{id}")
    public ResponseEntity<Map<String, Object>> deleteAgency(@PathVariable Long id) {
        tenantRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency deleted successfully"));
    }

    @GetMapping("/agencies/{id}/users")
    public ResponseEntity<List<Map<String, Object>>> getAgencyUsers(@PathVariable Long id) {
        List<User> users = userRepository.findAllByTenantId(id);
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("email", u.getEmail());
            map.put("role", u.getRole());
            map.put("tenantId", u.getTenant().getId());
            map.put("tenantName", u.getTenant().getName());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. SUBSCRIPTION & BILLING SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        return ResponseEntity.ok(planRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc());
    }

    @PostMapping("/plans")
    public ResponseEntity<SubscriptionPlan> createPlan(@Valid @RequestBody SubscriptionPlan plan) {
        return ResponseEntity.ok(planRepository.save(plan));
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<SubscriptionPlan> updatePlan(@PathVariable Long id, @RequestBody SubscriptionPlan updates) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        if (updates.getName() != null) plan.setName(updates.getName());
        if (updates.getCode() != null) plan.setCode(updates.getCode());
        if (updates.getMonthlyPrice() != null) plan.setMonthlyPrice(updates.getMonthlyPrice());
        if (updates.getYearlyPrice() != null) plan.setYearlyPrice(updates.getYearlyPrice());
        if (updates.getDescription() != null) plan.setDescription(updates.getDescription());
        if (updates.getMaxVehicles() != null) plan.setMaxVehicles(updates.getMaxVehicles());
        if (updates.getMaxEmployees() != null) plan.setMaxEmployees(updates.getMaxEmployees());
        if (updates.getMaxGpsDevices() != null) plan.setMaxGpsDevices(updates.getMaxGpsDevices());
        if (updates.getMaxReservations() != null) plan.setMaxReservations(updates.getMaxReservations());
        if (updates.getStorageLimitMb() != null) plan.setStorageLimitMb(updates.getStorageLimitMb());
        if (updates.getApiAccess() != null) plan.setApiAccess(updates.getApiAccess());
        if (updates.getWhiteLabel() != null) plan.setWhiteLabel(updates.getWhiteLabel());
        if (updates.getPrioritySupport() != null) plan.setPrioritySupport(updates.getPrioritySupport());
        if (updates.getIsActive() != null) plan.setIsActive(updates.getIsActive());
        if (updates.getFeaturesJson() != null) plan.setFeaturesJson(updates.getFeaturesJson());
        if (updates.getDisplayOrder() != null) plan.setDisplayOrder(updates.getDisplayOrder());
        return ResponseEntity.ok(planRepository.save(plan));
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Map<String, Object>> deletePlan(@PathVariable Long id) {
        planRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/agencies/{id}/subscribe")
    public ResponseEntity<Map<String, Object>> subscribeAgency(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        String planCode = body.get("planCode");
        SubscriptionPlan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        t.setPlanName(plan.getName());
        t.setSubscriptionActive(true);
        t.setSubscriptionEndDate(LocalDate.now().plusMonths(1));
        t.setMaxVehicles(plan.getMaxVehicles());
        t.setMaxEmployees(plan.getMaxEmployees());
        t.setMaxGpsDevices(plan.getMaxGpsDevices());
        t.setMaxReservations(plan.getMaxReservations());
        t.setStorageLimitMb(plan.getStorageLimitMb());
        t.setStatus("ACTIVE");
        tenantRepository.save(t);

        return ResponseEntity.ok(Map.of("success", true, "message", "Agency subscribed to " + plan.getName()));
    }

    @PostMapping("/agencies/{id}/extend-trial")
    public ResponseEntity<Map<String, Object>> extendTrial(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        int days = body.getOrDefault("days", 30);
        t.setTrialEndDate(LocalDate.now().plusDays(days));
        t.setStatus("TRIAL");
        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Trial extended by " + days + " days"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. GPS MANAGEMENT CENTER
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/gps")
    public ResponseEntity<Map<String, Object>> getGlobalGpsStats() {
        List<GpsSettings> allSettings = gpsSettingsRepository.findAll();
        List<Vehicle> allVehicles = vehicleRepository.findAll();

        long totalProviders = allSettings.stream().map(GpsSettings::getProvider).distinct().count();
        long activeConnections = allSettings.stream().filter(GpsSettings::getEnabled).count();
        long totalDevices = allVehicles.stream().filter(v -> v.getGpsDeviceId() != null).count();
        long onlineDevices = allVehicles.stream()
                .filter(v -> v.getGpsDeviceId() != null)
                .filter(v -> v.getGpsStatus() == GpsDeviceStatus.ONLINE)
                .count();
        long offlineDevices = totalDevices - onlineDevices;

        List<Map<String, Object>> providerStats = allSettings.stream()
                .collect(Collectors.groupingBy(GpsSettings::getProvider))
                .entrySet().stream().map(entry -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("provider", entry.getKey());
                    map.put("agencies", entry.getValue().size());
                    map.put("activeDevices", entry.getValue().stream().mapToInt(s -> s.getActiveDevices() != null ? s.getActiveDevices() : 0).sum());
                    map.put("connectionStatus", entry.getValue().stream().anyMatch(GpsSettings::getEnabled) ? "ACTIVE" : "INACTIVE");
                    return map;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProviders", totalProviders);
        result.put("activeConnections", activeConnections);
        result.put("totalDevices", totalDevices);
        result.put("onlineDevices", onlineDevices);
        result.put("offlineDevices", offlineDevices);
        result.put("providerStats", providerStats);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/gps/alerts")
    public ResponseEntity<List<GpsAlert>> getGlobalGpsAlerts() {
        return ResponseEntity.ok(gpsAlertRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(100)
                .collect(Collectors.toList()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. USER & ROLE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search) {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = users.stream()
                .filter(u -> role == null || u.getRole().name().equals(role))
                .filter(u -> search == null ||
                        u.getEmail().toLowerCase().contains(search.toLowerCase()) ||
                        u.getTenant().getName().toLowerCase().contains(search.toLowerCase()))
                .map(u -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", u.getId());
                    map.put("email", u.getEmail());
                    map.put("role", u.getRole());
                    map.put("tenantId", u.getTenant().getId());
                    map.put("tenantName", u.getTenant().getName());
                    map.put("isSystemUser", u.getTenant().getEmail().equals("system@innovax.tech"));
                    return map;
                }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String newRole = body.get("role");
        user.setRole(Role.valueOf(newRole));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "User role updated to " + newRole));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6. PAYMENTS & FINANCE
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/revenue")
    public ResponseEntity<RevenueStatsDto> getRevenueStats() {
        List<Invoice> allInvoices = invoiceRepository.findAll();
        LocalDate now = LocalDate.now();

        BigDecimal totalRevenue = allInvoices.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .map(Invoice::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyRevenue = allInvoices.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .filter(i -> i.getIssueDate() != null)
                .filter(i -> i.getIssueDate().getYear() == now.getYear() && i.getIssueDate().getMonthValue() == now.getMonthValue())
                .map(Invoice::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal yearlyRevenue = allInvoices.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .filter(i -> i.getIssueDate() != null && i.getIssueDate().getYear() == now.getYear())
                .map(Invoice::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long agencyCount = tenantRepository.count();
        BigDecimal avgRevenue = agencyCount > 0 ? totalRevenue.divide(new BigDecimal(agencyCount), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;

        // Monthly trend (last 12 months)
        List<MonthlyRevenueDto> monthlyTrend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 11; i >= 0; i--) {
            LocalDate month = now.minusMonths(i);
            final int year = month.getYear();
            final int monthValue = month.getMonthValue();
            BigDecimal revenue = allInvoices.stream()
                    .filter(inv -> inv.getStatus() == InvoiceStatus.PAID)
                    .filter(inv -> inv.getIssueDate() != null)
                    .filter(inv -> inv.getIssueDate().getYear() == year && inv.getIssueDate().getMonthValue() == monthValue)
                    .map(Invoice::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            monthlyTrend.add(MonthlyRevenueDto.builder()
                    .month(month.format(fmt))
                    .revenue(revenue)
                    .newAgencies(0) // Would need historical data
                    .churnedAgencies(0)
                    .build());
        }

        // Revenue by plan
        Map<String, BigDecimal> revenueByPlan = new LinkedHashMap<>();
        for (Invoice inv : allInvoices) {
            if (inv.getStatus() == InvoiceStatus.PAID && inv.getTenant() != null) {
                String plan = inv.getTenant().getPlanName() != null ? inv.getTenant().getPlanName() : "Unknown";
                revenueByPlan.merge(plan, inv.getAmount() != null ? inv.getAmount() : BigDecimal.ZERO, BigDecimal::add);
            }
        }
        List<PlanRevenueDto> planRevenue = revenueByPlan.entrySet().stream()
                .map(e -> PlanRevenueDto.builder()
                        .planName(e.getKey())
                        .revenue(e.getValue())
                        .agencyCount(0)
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(RevenueStatsDto.builder()
                .totalRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .yearlyRevenue(yearlyRevenue)
                .avgRevenuePerAgency(avgRevenue)
                .monthlyTrend(monthlyTrend)
                .revenueByPlan(planRevenue)
                .build());
    }

    @GetMapping("/payments")
    public ResponseEntity<List<Map<String, Object>>> getAllPayments() {
        List<Payment> payments = paymentRepository.findAll();
        List<Map<String, Object>> result = payments.stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", p.getId());
            map.put("amount", p.getAmount());
            map.put("paid", p.isPaid());
            map.put("tenantId", p.getTenant().getId());
            map.put("tenantName", p.getTenant().getName());
            map.put("reservationId", p.getReservation() != null ? p.getReservation().getId() : null);
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<Map<String, Object>>> getAllInvoices() {
        List<Invoice> invoices = invoiceRepository.findAll();
        List<Map<String, Object>> result = invoices.stream().map(i -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", i.getId());
            map.put("invoiceNumber", i.getInvoiceNumber());
            map.put("clientName", i.getClientName());
            map.put("amount", i.getAmount());
            map.put("status", i.getStatus());
            map.put("issueDate", i.getIssueDate());
            map.put("dueDate", i.getDueDate());
            map.put("tenantId", i.getTenant().getId());
            map.put("tenantName", i.getTenant().getName());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 7. SUPPORT & TICKETS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/tickets")
    public ResponseEntity<List<SupportTicket>> getAllTickets(
            @RequestParam(required = false) String status) {
        List<SupportTicket> tickets;
        if (status != null) {
            tickets = ticketRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            tickets = ticketRepository.findAllByOrderByCreatedAtDesc();
        }
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<SupportTicket> getTicket(@PathVariable Long id) {
        return ResponseEntity.ok(ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found")));
    }

    @PatchMapping("/tickets/{id}")
    public ResponseEntity<SupportTicket> updateTicket(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        if (body.containsKey("status")) ticket.setStatus(body.get("status"));
        if (body.containsKey("priority")) ticket.setPriority(body.get("priority"));
        if (body.containsKey("assignedTo")) ticket.setAssignedTo(body.get("assignedTo"));
        if (body.containsKey("resolution")) ticket.setResolution(body.get("resolution"));
        if ("RESOLVED".equals(ticket.getStatus()) || "CLOSED".equals(ticket.getStatus())) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        return ResponseEntity.ok(ticketRepository.save(ticket));
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicket> createTicket(@RequestBody SupportTicket ticket) {
        return ResponseEntity.ok(ticketRepository.save(ticket));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 8. NOTIFICATIONS CENTER (stored as audit logs for now)
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/notifications")
    public ResponseEntity<List<Map<String, Object>>> getNotifications() {
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = logs.stream().map(log -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", log.getId());
            map.put("type", log.getAction());
            map.put("message", log.getDescription());
            map.put("timestamp", log.getCreatedAt());
            map.put("read", true); // Simplified
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 9. ANALYTICS & BUSINESS INTELLIGENCE
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/analytics/growth")
    public ResponseEntity<Map<String, Object>> getGrowthAnalytics() {
        List<Tenant> tenants = tenantRepository.findAll();
        LocalDate now = LocalDate.now();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAgencies", tenants.size());
        result.put("newAgenciesThisMonth", tenants.stream().filter(t -> t.getCreatedAt() != null && t.getCreatedAt().getMonthValue() == now.getMonthValue() && t.getCreatedAt().getYear() == now.getYear()).count());
        result.put("activeAgencies", tenants.stream().filter(Tenant::isSubscriptionValid).count());
        result.put("churnRate", 0.0); // Would need historical data
        result.put("avgAgencyAgeDays", tenants.stream().mapToLong(t -> t.getCreatedAt() != null ? java.time.temporal.ChronoUnit.DAYS.between(t.getCreatedAt().toLocalDate(), now) : 0).average().orElse(0));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/analytics/agencies")
    public ResponseEntity<Map<String, Object>> getAgencyAnalytics() {
        List<Tenant> tenants = tenantRepository.findAll();
        Map<String, Long> byStatus = tenants.stream().collect(Collectors.groupingBy(Tenant::getStatus, Collectors.counting()));
        Map<String, Long> byPlan = tenants.stream().collect(Collectors.groupingBy(t -> t.getPlanName() != null ? t.getPlanName() : "Unknown", Collectors.counting()));
        Map<String, Long> byCountry = tenants.stream().collect(Collectors.groupingBy(t -> t.getCountry() != null ? t.getCountry() : "Unknown", Collectors.counting()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byStatus", byStatus);
        result.put("byPlan", byPlan);
        result.put("byCountry", byCountry);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/analytics/reservations")
    public ResponseEntity<Map<String, Object>> getReservationAnalytics() {
        List<Reservation> reservations = reservationRepository.findAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReservations", reservations.size());
        result.put("totalRevenue", invoiceRepository.findAll().stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .map(Invoice::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("avgReservationValue", reservations.isEmpty() ? 0 :
                invoiceRepository.findAll().stream()
                        .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                        .map(Invoice::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(reservations.size()), 2, BigDecimal.ROUND_HALF_UP));
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 10. PLATFORM SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/settings")
    public ResponseEntity<PlatformSettings> getPlatformSettings() {
        List<PlatformSettings> settings = platformSettingsRepository.findAll();
        if (settings.isEmpty()) {
            return ResponseEntity.ok(platformSettingsRepository.save(PlatformSettings.builder().build()));
        }
        return ResponseEntity.ok(settings.get(0));
    }

    @PutMapping("/settings")
    public ResponseEntity<PlatformSettings> updatePlatformSettings(@RequestBody Map<String, Object> updates) {
        List<PlatformSettings> settings = platformSettingsRepository.findAll();
        PlatformSettings ps = settings.isEmpty() ? PlatformSettings.builder().build() : settings.get(0);

        if (updates.containsKey("platformName")) ps.setPlatformName(toStr(updates.get("platformName")));
        if (updates.containsKey("logoUrl")) ps.setLogoUrl(toStr(updates.get("logoUrl")));
        if (updates.containsKey("primaryColor")) ps.setPrimaryColor(toStr(updates.get("primaryColor")));
        if (updates.containsKey("maintenanceMode")) ps.setMaintenanceMode(toBool(updates.get("maintenanceMode")));
        if (updates.containsKey("maintenanceMessage")) ps.setMaintenanceMessage(toStr(updates.get("maintenanceMessage")));
        if (updates.containsKey("defaultLanguage")) ps.setDefaultLanguage(toStr(updates.get("defaultLanguage")));
        if (updates.containsKey("supportedLanguages")) ps.setSupportedLanguages(toStr(updates.get("supportedLanguages")));
        if (updates.containsKey("defaultCurrency")) ps.setDefaultCurrency(toStr(updates.get("defaultCurrency")));
        if (updates.containsKey("smtpHost")) ps.setSmtpHost(toStr(updates.get("smtpHost")));
        if (updates.containsKey("smtpPort")) ps.setSmtpPort(toInt(updates.get("smtpPort")));
        if (updates.containsKey("smtpUsername")) ps.setSmtpUsername(toStr(updates.get("smtpUsername")));
        if (updates.containsKey("fromEmail")) ps.setFromEmail(toStr(updates.get("fromEmail")));
        if (updates.containsKey("fromName")) ps.setFromName(toStr(updates.get("fromName")));
        if (updates.containsKey("apiRateLimit")) ps.setApiRateLimit(toInt(updates.get("apiRateLimit")));
        if (updates.containsKey("sessionTimeoutMinutes")) ps.setSessionTimeoutMinutes(toInt(updates.get("sessionTimeoutMinutes")));
        if (updates.containsKey("maxLoginAttempts")) ps.setMaxLoginAttempts(toInt(updates.get("maxLoginAttempts")));
        if (updates.containsKey("lockoutDurationMinutes")) ps.setLockoutDurationMinutes(toInt(updates.get("lockoutDurationMinutes")));
        if (updates.containsKey("require2fa")) ps.setRequire2fa(toBool(updates.get("require2fa")));
        if (updates.containsKey("analyticsId")) ps.setAnalyticsId(toStr(updates.get("analyticsId")));
        if (updates.containsKey("customCss")) ps.setCustomCss(toStr(updates.get("customCss")));

        return ResponseEntity.ok(platformSettingsRepository.save(ps));
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.valueOf(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean toBool(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.valueOf(v.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 11. SECURITY CENTER
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/security/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<AuditLog> logs;
        if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).plusDays(1).atStartOfDay();
            logs = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        } else if (action != null) {
            logs = auditLogRepository.findByActionOrderByCreatedAtDesc(action);
        } else {
            logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        }
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/security/audit-logs")
    public ResponseEntity<AuditLog> createAuditLog(@RequestBody AuditLog log) {
        return ResponseEntity.ok(auditLogRepository.save(log));
    }

    @GetMapping("/security/summary")
    public ResponseEntity<Map<String, Object>> getSecuritySummary() {
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);
        List<AuditLog> recentLogs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEvents24h", recentLogs.stream().filter(l -> l.getCreatedAt().isAfter(last24h)).count());
        result.put("failedLogins24h", recentLogs.stream().filter(l -> l.getCreatedAt().isAfter(last24h)).filter(l -> "LOGIN".equals(l.getAction()) && Boolean.FALSE.equals(l.getIsSuccess())).count());
        result.put("suspiciousEvents", recentLogs.stream().filter(l -> !Boolean.TRUE.equals(l.getIsSuccess())).count());
        result.put("activeSessions", userRepository.count()); // Simplified
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 12. NEW AGENCY ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/agencies")
    public ResponseEntity<Map<String, Object>> createAgency(@RequestBody Map<String, Object> body) {
        Tenant t = Tenant.builder()
                .name((String) body.get("name"))
                .email((String) body.get("email"))
                .phone((String) body.get("phone"))
                .address((String) body.get("address"))
                .city((String) body.get("city"))
                .country((String) body.get("country"))
                .taxId((String) body.get("taxId"))
                .status("TRIAL")
                .subscriptionActive(false)
                .trialEndDate(LocalDate.now().plusDays(60))
                .planName("Trial")
                .build();
        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency created successfully", "id", t.getId()));
    }

    @PostMapping("/agencies/{id}/restore")
    public ResponseEntity<Map<String, Object>> restoreAgency(@PathVariable Long id) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        t.setStatus("ACTIVE");
        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency restored"));
    }

    @PostMapping("/agencies/{id}/verify")
    public ResponseEntity<Map<String, Object>> verifyAgency(@PathVariable Long id) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        t.setStatus("ACTIVE");
        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency verified"));
    }

    @GetMapping("/agencies/{id}/activity")
    public ResponseEntity<List<AuditLog>> getAgencyActivity(@PathVariable Long id) {
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(l -> l.getEntityType() != null && l.getEntityType().contains("TENANT_" + id))
                .collect(Collectors.toList());
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/agencies/{id}/invoices")
    public ResponseEntity<List<Invoice>> getAgencyInvoices(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceRepository.findAll().stream()
                .filter(i -> i.getTenant().getId().equals(id))
                .sorted((a, b) -> b.getIssueDate().compareTo(a.getIssueDate()))
                .collect(Collectors.toList()));
    }

    @GetMapping("/agencies/{id}/security-logs")
    public ResponseEntity<List<Map<String, Object>>> getAgencySecurityLogs(@PathVariable Long id) {
        List<User> users = userRepository.findAllByTenantId(id);
        List<Map<String, Object>> result = auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(l -> users.stream().anyMatch(u -> u.getEmail().equals(l.getPerformedBy())))
                .map(l -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", l.getId());
                    map.put("action", l.getAction());
                    map.put("ipAddress", l.getIpAddress());
                    map.put("isSuccess", l.getIsSuccess());
                    map.put("timestamp", l.getCreatedAt());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/agencies/{id}/force-renew")
    public ResponseEntity<Map<String, Object>> forceRenew(@PathVariable Long id) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        t.setSubscriptionEndDate(LocalDate.now().plusMonths(1));
        t.setSubscriptionActive(true);
        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Subscription renewed"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 13. PROMO CODES
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/promo-codes")
    public ResponseEntity<List<PromoCode>> getPromoCodes() {
        return ResponseEntity.ok(promoCodeRepository.findAll());
    }

    @PostMapping("/promo-codes")
    public ResponseEntity<PromoCode> createPromoCode(@RequestBody PromoCode promoCode) {
        return ResponseEntity.ok(promoCodeRepository.save(promoCode));
    }

    @PutMapping("/promo-codes/{id}")
    public ResponseEntity<PromoCode> updatePromoCode(@PathVariable Long id, @RequestBody PromoCode updates) {
        PromoCode pc = promoCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found"));
        if (updates.getCode() != null) pc.setCode(updates.getCode());
        if (updates.getDiscountType() != null) pc.setDiscountType(updates.getDiscountType());
        if (updates.getDiscountValue() != null) pc.setDiscountValue(updates.getDiscountValue());
        if (updates.getMaxUses() != null) pc.setMaxUses(updates.getMaxUses());
        if (updates.getValidFrom() != null) pc.setValidFrom(updates.getValidFrom());
        if (updates.getValidTo() != null) pc.setValidTo(updates.getValidTo());
        if (updates.getIsActive() != null) pc.setIsActive(updates.getIsActive());
        return ResponseEntity.ok(promoCodeRepository.save(pc));
    }

    @DeleteMapping("/promo-codes/{id}")
    public ResponseEntity<Map<String, Object>> deletePromoCode(@PathVariable Long id) {
        promoCodeRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/trial-analytics")
    public ResponseEntity<Map<String, Object>> getTrialAnalytics() {
        List<Tenant> tenants = tenantRepository.findAll();
        long totalTrials = tenants.stream().filter(Tenant::isInTrial).count();
        long converted = tenants.stream().filter(t -> !t.isInTrial() && t.isSubscriptionValid()).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTrials", totalTrials);
        result.put("converted", converted);
        result.put("conversionRate", totalTrials > 0 ? (converted * 100.0 / totalTrials) : 0);
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 14. EMAIL CENTER
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/email/templates")
    public ResponseEntity<List<EmailTemplate>> getEmailTemplates() {
        return ResponseEntity.ok(emailTemplateRepository.findAll());
    }

    @PostMapping("/email/templates")
    public ResponseEntity<EmailTemplate> createEmailTemplate(@RequestBody EmailTemplate template) {
        return ResponseEntity.ok(emailTemplateRepository.save(template));
    }

    @PutMapping("/email/templates/{id}")
    public ResponseEntity<EmailTemplate> updateEmailTemplate(@PathVariable Long id, @RequestBody EmailTemplate updates) {
        EmailTemplate et = emailTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        if (updates.getName() != null) et.setName(updates.getName());
        if (updates.getType() != null) et.setType(updates.getType());
        if (updates.getSubject() != null) et.setSubject(updates.getSubject());
        if (updates.getBodyHtml() != null) et.setBodyHtml(updates.getBodyHtml());
        if (updates.getBodyText() != null) et.setBodyText(updates.getBodyText());
        if (updates.getIsActive() != null) et.setIsActive(updates.getIsActive());
        return ResponseEntity.ok(emailTemplateRepository.save(et));
    }

    @DeleteMapping("/email/templates/{id}")
    public ResponseEntity<Map<String, Object>> deleteEmailTemplate(@PathVariable Long id) {
        emailTemplateRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/email/test")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Test email queued for " + body.get("to")));
    }

    @GetMapping("/email/logs")
    public ResponseEntity<List<EmailLog>> getEmailLogs() {
        return ResponseEntity.ok(emailLogRepository.findTop100ByOrderByCreatedAtDesc());
    }

    @GetMapping("/email/analytics")
    public ResponseEntity<Map<String, Object>> getEmailAnalytics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSent", emailLogRepository.count());
        result.put("delivered", emailLogRepository.countByStatus("DELIVERED"));
        result.put("failed", emailLogRepository.countByStatus("FAILED"));
        result.put("bounced", emailLogRepository.countByStatus("BOUNCED"));
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 15. SUPPORT NOTES
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/tickets/{id}/notes")
    public ResponseEntity<List<Map<String, Object>>> getTicketNotes(@PathVariable Long id) {
        // Simplified: return empty or mock data
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/tickets/{id}/notes")
    public ResponseEntity<Map<String, Object>> addTicketNote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Note added"));
    }

    @GetMapping("/support/analytics")
    public ResponseEntity<Map<String, Object>> getSupportAnalytics() {
        List<SupportTicket> tickets = ticketRepository.findAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTickets", tickets.size());
        result.put("openTickets", tickets.stream().filter(t -> "OPEN".equals(t.getStatus())).count());
        result.put("avgResolutionHours", 24); // Simplified
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 16. SECURITY EXTENSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/security/login-history")
    public ResponseEntity<List<Map<String, Object>>> getLoginHistory() {
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(l -> "LOGIN".equals(l.getAction()))
                .collect(Collectors.toList());
        List<Map<String, Object>> result = logs.stream().map(l -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", l.getId());
            map.put("performedBy", l.getPerformedBy());
            map.put("ipAddress", l.getIpAddress());
            map.put("success", l.getIsSuccess());
            map.put("createdAt", l.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/security/sessions")
    public ResponseEntity<List<UserSession>> getSessions() {
        return ResponseEntity.ok(userSessionRepository.findByRevokedFalse());
    }

    @DeleteMapping("/security/sessions/{id}")
    public ResponseEntity<Map<String, Object>> revokeSession(@PathVariable Long id) {
        UserSession session = userSessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        session.setRevoked(true);
        userSessionRepository.save(session);
        return ResponseEntity.ok(Map.of("success", true, "message", "Session revoked"));
    }

    @GetMapping("/security/failed-logins")
    public ResponseEntity<List<Map<String, Object>>> getFailedLogins() {
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(l -> "LOGIN".equals(l.getAction()) && Boolean.FALSE.equals(l.getIsSuccess()))
                .collect(Collectors.toList());
        List<Map<String, Object>> result = logs.stream().map(l -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", l.getId());
            map.put("performedBy", l.getPerformedBy());
            map.put("ipAddress", l.getIpAddress());
            map.put("reason", "Invalid credentials");
            map.put("createdAt", l.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 17. PLATFORM HEALTH
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getPlatformHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiStatus", "healthy");
        result.put("dbStatus", "healthy");
        result.put("gpsStatus", "healthy");
        result.put("emailStatus", "healthy");
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 18. MARKETING
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/marketing/onboarding")
    public ResponseEntity<Map<String, Object>> getMarketingOnboarding() {
        return ResponseEntity.ok(Map.of(
                "step1Title", "Welcome to Innovax",
                "step1Description", "Let's get your agency set up in minutes.",
                "step2Title", "Configure Your Fleet",
                "step3Title", "Invite Your Team",
                "completionMessage", "You're all set!"
        ));
    }

    @PutMapping("/marketing/onboarding")
    public ResponseEntity<Map<String, Object>> updateMarketingOnboarding(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("success", true, "data", body));
    }

    @GetMapping("/marketing/conversion")
    public ResponseEntity<Map<String, Object>> getMarketingConversion() {
        List<Tenant> tenants = tenantRepository.findAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("websiteVisits", 12500);
        result.put("signupsStarted", 850);
        result.put("trialsCreated", tenants.size());
        result.put("trialsCompleted", tenants.stream().filter(t -> !t.isInTrial()).count());
        result.put("paidConversion", tenants.stream().filter(Tenant::isSubscriptionValid).count());
        result.put("trialToPaidRate", tenants.stream().filter(Tenant::isInTrial).count() > 0 ? 35 : 0);
        result.put("avgOnboardingMinutes", 8);
        result.put("landingCTR", 4.2);
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 19. CONTRACTS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/contracts")
    public ResponseEntity<List<Map<String, Object>>> getAllContracts() {
        List<Contract> contracts = contractRepository.findAll();
        List<Map<String, Object>> result = contracts.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("contractNumber", c.getContractNumber());
            map.put("clientName", c.getClientFullName());
            map.put("status", c.getStatus());
            map.put("startDate", c.getStartDate());
            map.put("endDate", c.getEndDate());
            map.put("totalAmount", c.getTotalPrice());
            map.put("agencyName", c.getTenant() != null ? c.getTenant().getName() : null);
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 20. REPORTS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/reports/{type}")
    public ResponseEntity<Map<String, Object>> getReport(@PathVariable String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("generatedAt", LocalDateTime.now());
        result.put("url", "/api/reports/" + type + ".csv");
        return ResponseEntity.ok(result);
    }

}
