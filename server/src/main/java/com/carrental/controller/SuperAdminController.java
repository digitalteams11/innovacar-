package com.carrental.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.carrental.dto.superadmin.*;
import com.carrental.entity.*;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.GpsDeviceStatus;
import com.carrental.repository.*;
import com.carrental.service.SystemHealthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Super Admin Controller — Central brain of the SaaS platform.
 * Innovax Technologies uses these endpoints to manage the entire ecosystem.
 *
 * All endpoints require SUPER_ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final TenantRepository tenantRepository;
    private final WhiteLabelSettingsRepository whiteLabelSettingsRepository;
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
    private final com.carrental.service.PlatformSettingsService platformSettingsService;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository promoCodeRedemptionRepository;
    private final PromoCodePlanLinkRepository promoCodePlanLinkRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailLogRepository emailLogRepository;
    private final com.carrental.service.SmtpMailService smtpMailService;
    private final UserSessionRepository userSessionRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final ObjectMapper objectMapper;
    private final SupportMessageRepository supportMessageRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final OnboardingProgressRepository onboardingProgressRepository;
    private final SystemHealthService systemHealthService;
    private final NotificationReadRepository notificationReadRepository;
    private final AgencyBalanceTransactionRepository balanceTransactionRepository;
    private final AnnouncementRepository announcementRepository;
    private final CancellationRequestRepository cancellationRequestRepository;
    private final com.carrental.util.EncryptionUtil encryptionUtil;
    private final com.carrental.service.PlatformEmailService platformEmailService;
    private final com.carrental.service.EmailTemplateService emailTemplateService;
    private final com.carrental.service.SupportRoutingService supportRoutingService;

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

        // isAccountBlocked() covers BLOCKED/SUSPENDED/INACTIVE — these must be
        // their own buckets, not lumped into "expired", which previously made
        // every blocked/suspended agency mislabeled as merely expired on the
        // dashboard's status breakdown.
        long activeAgencies = allTenants.stream().filter(Tenant::isSubscriptionValid).count();
        long trialAgencies = allTenants.stream()
                .filter(t -> !t.isAccountBlocked()).filter(Tenant::isInTrial).count();
        long blockedAgencies = allTenants.stream()
                .filter(t -> "BLOCKED".equalsIgnoreCase(t.getStatus()) || "INACTIVE".equalsIgnoreCase(t.getStatus()))
                .count();
        long suspendedAgencies = allTenants.stream()
                .filter(t -> "SUSPENDED".equalsIgnoreCase(t.getStatus())).count();
        long expiredAgencies = allTenants.stream()
                .filter(t -> !t.isAccountBlocked() && !t.isSubscriptionValid() && !t.isInTrial())
                .count();

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
                .blockedAgencies(blockedAgencies)
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
                    map.put("verificationStatus", t.getVerificationStatus());
                    map.put("verifiedAt", t.getVerifiedAt());
                    map.put("planName", t.getPlanName());
                    map.put("subscriptionActive", t.isSubscriptionValid());
                    map.put("subscriptionEndDate", t.getSubscriptionEndDate());
                    map.put("trialEndDate", t.getTrialEndDate());
                    map.put("inTrial", t.isInTrial());
                    map.put("balance", t.getBalance() == null ? BigDecimal.ZERO : t.getBalance());
                    map.put("freeAccessUntil", t.getFreeAccessUntil());
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
                .verificationStatus(t.getVerificationStatus())
                .verifiedAt(t.getVerifiedAt())
                .verifiedBy(t.getVerifiedBy())
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
                .balance(t.getBalance() == null ? BigDecimal.ZERO : t.getBalance())
                .freeAccessUntil(t.getFreeAccessUntil())
                .freeAccessReason(t.getFreeAccessReason())
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
        String previousStatus = t.getStatus();
        String newStatus = body.get("status");
        t.setStatus(newStatus);
        if ("SUSPENDED".equals(newStatus) || "BLOCKED".equals(newStatus) || "INACTIVE".equals(newStatus)) {
            t.setSubscriptionActive(false);
        } else if ("ACTIVE".equals(newStatus)) {
            t.setSubscriptionActive(true);
        }
        tenantRepository.save(t);
        String action = "SUSPENDED".equals(newStatus) ? "AGENCY_SUSPENDED"
                : "ACTIVE".equals(newStatus) ? "AGENCY_REACTIVATED"
                : "AGENCY_STATUS_CHANGED";
        logAgencyAction(t, action, "Status changed from " + previousStatus + " to " + newStatus
                + (body.get("reason") != null ? " — " + body.get("reason") : ""));
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
        return ResponseEntity.ok(planRepository.findAll(
                org.springframework.data.domain.Sort.by("displayOrder", "id")));
    }

    @PatchMapping("/plans/{id}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivatePlan(@PathVariable Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        plan.setIsActive(false);
        planRepository.save(plan);
        return ResponseEntity.ok(Map.of("success", true, "message", "Plan deactivated"));
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
        if (updates.getWhopCheckoutUrlMonthly() != null) plan.setWhopCheckoutUrlMonthly(updates.getWhopCheckoutUrlMonthly());
        if (updates.getWhopCheckoutUrlYearly() != null) plan.setWhopCheckoutUrlYearly(updates.getWhopCheckoutUrlYearly());
        if (updates.getWhopProductId() != null) plan.setWhopProductId(updates.getWhopProductId());
        if (updates.getWhopPlanId() != null) plan.setWhopPlanId(updates.getWhopPlanId());
        if (updates.getWhopPriceId() != null) plan.setWhopPriceId(updates.getWhopPriceId());
        if (updates.getCurrency() != null) plan.setCurrency(updates.getCurrency());
        if (updates.getTrialDays() != null) plan.setTrialDays(updates.getTrialDays());
        if (updates.getClientLimit() != null) plan.setClientLimit(updates.getClientLimit());
        if (updates.getContractLimit() != null) plan.setContractLimit(updates.getContractLimit());
        if (updates.getBillingCycleAllowedMonthly() != null) plan.setBillingCycleAllowedMonthly(updates.getBillingCycleAllowedMonthly());
        if (updates.getBillingCycleAllowedYearly() != null) plan.setBillingCycleAllowedYearly(updates.getBillingCycleAllowedYearly());
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

    /** {@code id} is the tenant whose GPS provider configuration is being overridden by Super Admin. */
    @PutMapping("/gps/providers/{id}")
    public ResponseEntity<Map<String, Object>> updateGpsProvider(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        GpsSettings settings = gpsSettingsRepository.findByTenantId(id)
                .orElseGet(() -> GpsSettings.builder().tenant(tenant).provider("CUSTOM").build());
        if (body.get("provider") != null) settings.setProvider(body.get("provider").toString());
        if (body.get("baseUrl") != null) settings.setBaseUrl(body.get("baseUrl").toString());
        if (body.containsKey("enabled")) settings.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        gpsSettingsRepository.save(settings);
        return ResponseEntity.ok(Map.of("success", true, "message", "GPS provider configuration updated"));
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

    @GetMapping("/users/{id}/sessions")
    public ResponseEntity<List<UserSession>> getUserSessions(@PathVariable Long id) {
        return ResponseEntity.ok(userSessionRepository
                .findByUserIdAndRevokedFalseAndExpiresAtAfter(id, LocalDateTime.now()));
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

    @PostMapping("/invoices/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryInvoicePayment(@PathVariable Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (invoice.getStatus() != InvoiceStatus.OVERDUE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Only overdue invoices can be retried",
                    "errorCode", "INVOICE_NOT_OVERDUE"));
        }
        invoice.setStatus(InvoiceStatus.PENDING);
        invoiceRepository.save(invoice);
        return ResponseEntity.ok(Map.of("success", true, "message", "Payment retry initiated for invoice " + invoice.getInvoiceNumber()));
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

    @PostMapping("/tickets/{id}/resend-email")
    public ResponseEntity<Map<String, Object>> resendTicketEmail(@PathVariable Long id) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        if (ticket.getDestinationEmail() == null || ticket.getDestinationEmail().isBlank()) {
            ticket.setDestinationEmail(supportRoutingService.resolveDestinationEmail(ticket.getChannel(), ticket.getCategory()));
        }
        platformEmailService.sendSupportTicketCreatedInternal(ticket);
        SupportTicket refreshed = ticketRepository.findById(id).orElse(ticket);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Email resend attempted",
                "emailStatus", refreshed.getEmailStatus() != null ? refreshed.getEmailStatus() : "PENDING"
        ));
    }

    @GetMapping("/tickets/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> getTicketMessages(@PathVariable Long id) {
        ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        List<SupportMessage> messages = supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(id);
        messages.stream()
                .filter(message -> "AGENCY".equals(message.getSenderType()) && message.getReadAt() == null)
                .forEach(message -> message.setReadAt(LocalDateTime.now()));
        supportMessageRepository.saveAll(messages);
        return ResponseEntity.ok(messages.stream().map(this::supportMessageMap).toList());
    }

    @PostMapping("/tickets/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendTicketMessage(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        String message = toStr(body.get("message"));
        String attachmentData = toStr(body.get("attachmentData"));
        if ((message == null || message.isBlank()) && (attachmentData == null || attachmentData.isBlank())) {
            throw new IllegalArgumentException("A message or attachment is required");
        }
        if (attachmentData != null && attachmentData.length() > 7_000_000) {
            throw new IllegalArgumentException("Attachment must not exceed 5 MB");
        }
        SupportMessage saved = supportMessageRepository.save(SupportMessage.builder()
                .ticket(ticket)
                .senderName("Innovax Support")
                .senderType("SUPPORT")
                .message(message)
                .attachmentName(toStr(body.get("attachmentName")))
                .attachmentType(toStr(body.get("attachmentType")))
                .attachmentData(attachmentData)
                .readAt(LocalDateTime.now())
                .build());
        if ("OPEN".equals(ticket.getStatus())) {
            ticket.setStatus("IN_PROGRESS");
            ticketRepository.save(ticket);
        }
        platformEmailService.sendSupportReplyNotification(ticket, saved);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Reply sent successfully",
                "item", supportMessageMap(saved)
        ));
    }

    private Map<String, Object> supportMessageMap(SupportMessage message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", message.getId());
        value.put("senderName", message.getSenderName());
        value.put("senderType", message.getSenderType());
        value.put("message", message.getMessage() == null ? "" : message.getMessage());
        value.put("attachmentName", message.getAttachmentName() == null ? "" : message.getAttachmentName());
        value.put("attachmentType", message.getAttachmentType() == null ? "" : message.getAttachmentType());
        value.put("attachmentData", message.getAttachmentData() == null ? "" : message.getAttachmentData());
        value.put("read", message.getReadAt() != null);
        value.put("createdAt", message.getCreatedAt());
        return value;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 8. NOTIFICATIONS CENTER (stored as audit logs for now)
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/notifications")
    public ResponseEntity<List<Map<String, Object>>> getNotifications() {
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        Set<Long> readIds = notificationReadRepository.findByAdminUserId(currentUserId()).stream()
                .map(NotificationRead::getNotificationId)
                .collect(Collectors.toSet());
        List<Map<String, Object>> result = logs.stream().map(log -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", log.getId());
            map.put("type", log.getAction());
            map.put("message", log.getDescription());
            map.put("timestamp", log.getCreatedAt());
            map.put("read", readIds.contains(log.getId()));
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Object>> markNotificationRead(@PathVariable Long id) {
        Long adminId = currentUserId();
        if (!notificationReadRepository.existsByAdminUserIdAndNotificationId(adminId, id)) {
            notificationReadRepository.save(NotificationRead.builder()
                    .adminUserId(adminId)
                    .notificationId(id)
                    .build());
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Notification marked as read"));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Object>> markAllNotificationsRead() {
        Long adminId = currentUserId();
        Set<Long> alreadyRead = notificationReadRepository.findByAdminUserId(adminId).stream()
                .map(NotificationRead::getNotificationId)
                .collect(Collectors.toSet());
        List<AuditLog> logs = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        List<NotificationRead> toSave = logs.stream()
                .filter(log -> !alreadyRead.contains(log.getId()))
                .map(log -> NotificationRead.builder().adminUserId(adminId).notificationId(log.getId()).build())
                .collect(Collectors.toList());
        notificationReadRepository.saveAll(toSave);
        return ResponseEntity.ok(Map.of("success", true, "message", "All notifications marked as read"));
    }

    private Long currentUserId() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user.getId();
        throw new IllegalStateException("No authenticated Super Admin user found");
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
        return ResponseEntity.ok(platformSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<PlatformSettings> updatePlatformSettings(@RequestBody Map<String, Object> updates) {
        PlatformSettings ps = platformSettings();

        if (updates.containsKey("platformName")) ps.setPlatformName(toStr(updates.get("platformName")));
        if (updates.containsKey("logoUrl")) ps.setLogoUrl(toStr(updates.get("logoUrl")));
        if (updates.containsKey("primaryColor")) ps.setPrimaryColor(toStr(updates.get("primaryColor")));
        if (updates.containsKey("maintenanceMode")) ps.setMaintenanceMode(toBool(updates.get("maintenanceMode")));
        if (updates.containsKey("maintenanceMessage")) ps.setMaintenanceMessage(toStr(updates.get("maintenanceMessage")));
        if (updates.containsKey("defaultLanguage")) ps.setDefaultLanguage(toStr(updates.get("defaultLanguage")));
        if (updates.containsKey("supportedLanguages")) ps.setSupportedLanguages(toStr(updates.get("supportedLanguages")));
        if (updates.containsKey("defaultCurrency")) ps.setDefaultCurrency(toStr(updates.get("defaultCurrency")));
        // SMTP fields (smtpHost/smtpPort/smtpUsername/smtpPassword/fromEmail/fromName/etc.) are
        // intentionally NOT handled here. They are owned exclusively by PUT /email/settings
        // (updateSmtpSettings) — this endpoint used to also accept a subset of them (host/port/
        // username/fromEmail/fromName but never password/enabled/TLS), which let an admin using
        // the generic Settings page save a "half configured" SMTP row that looked saved but could
        // never actually send. See Email Center → SMTP for the one true SMTP form.
        if (updates.containsKey("apiRateLimit")) ps.setApiRateLimit(toInt(updates.get("apiRateLimit")));
        if (updates.containsKey("sessionTimeoutMinutes")) ps.setSessionTimeoutMinutes(toInt(updates.get("sessionTimeoutMinutes")));
        if (updates.containsKey("maxLoginAttempts")) ps.setMaxLoginAttempts(toInt(updates.get("maxLoginAttempts")));
        if (updates.containsKey("lockoutDurationMinutes")) ps.setLockoutDurationMinutes(toInt(updates.get("lockoutDurationMinutes")));
        if (updates.containsKey("require2fa")) ps.setRequire2fa(toBool(updates.get("require2fa")));
        if (updates.containsKey("analyticsId")) ps.setAnalyticsId(toStr(updates.get("analyticsId")));
        if (updates.containsKey("customCss")) ps.setCustomCss(toStr(updates.get("customCss")));
        if (updates.containsKey("themePresetsJson")) ps.setThemePresetsJson(toStr(updates.get("themePresetsJson")));

        return ResponseEntity.ok(platformSettingsRepository.save(ps));
    }

    @PostMapping("/themes/apply")
    public ResponseEntity<Map<String, Object>> applyTheme(@RequestBody Map<String, Object> request) {
        Object rawAppearance = request.get("appearance");
        if (!(rawAppearance instanceof Map<?, ?> appearance) || appearance.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "A valid appearance preset is required"
            ));
        }

        List<Tenant> targets;
        if (Boolean.TRUE.equals(toBool(request.get("applyToAll")))) {
            targets = tenantRepository.findAll();
        } else {
            Set<Long> tenantIds = parseTenantIds(request.get("tenantIds"));
            targets = tenantIds.isEmpty() ? List.of() : tenantRepository.findAllById(tenantIds);
        }

        if (targets.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Select at least one agency"
            ));
        }

        try {
            String appearanceJson = objectMapper.writeValueAsString(appearance);
            List<TenantSettings> settings = targets.stream().map(tenant ->
                    tenantSettingsRepository.findByTenantId(tenant.getId()).orElseGet(() ->
                            TenantSettings.builder().tenant(tenant).build()
                    )
            ).peek(value -> value.setAppearanceJson(appearanceJson)).toList();
            tenantSettingsRepository.saveAll(settings);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Theme applied successfully",
                    "updatedAgencies", settings.size()
            ));
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "The appearance preset could not be processed"
            ));
        }
    }

    private Set<Long> parseTenantIds(Object value) {
        if (!(value instanceof Collection<?> values)) return Set.of();
        return values.stream().map(item -> {
            if (item instanceof Number number) return number.longValue();
            try {
                return Long.valueOf(item.toString());
            } catch (NumberFormatException exception) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    private String trimStr(Object v) {
        String s = toStr(v);
        return s != null ? s.strip() : null;
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

    private Long toLong(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        if (v instanceof Long) return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.valueOf(v.toString());
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

    @GetMapping("/security/summary")
    public ResponseEntity<Map<String, Object>> getSecuritySummary() {
        LocalDateTime last24h = LocalDateTime.now().minusHours(24);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEvents24h", loginAttemptRepository.findTop100ByOrderByAttemptedAtDesc().stream()
                .filter(item -> item.getAttemptedAt().isAfter(last24h)).count());
        result.put("failedLogins24h", loginAttemptRepository.countBySuccessfulFalseAndAttemptedAtAfter(last24h));
        result.put("suspiciousEvents", loginAttemptRepository.countBySuspiciousTrue());
        result.put("activeSessions", userSessionRepository
                .findByRevokedFalseAndExpiresAtAfter(LocalDateTime.now()).size());
        result.put("blockedDevices", trustedDeviceRepository.countByBlockedTrue());
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
        t.setVerificationStatus("VERIFIED");
        t.setVerifiedAt(LocalDateTime.now());
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) t.setVerifiedBy(user.getId());
        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency verified"));
    }

    @PostMapping("/agencies/{id}/verification/reset")
    public ResponseEntity<Map<String, Object>> resetAgencyVerification(@PathVariable Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        tenant.setVerificationStatus("PENDING_VERIFICATION");
        tenant.setVerifiedAt(null);
        tenant.setVerifiedBy(null);
        tenantRepository.save(tenant);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency verification reset"));
    }

    @PutMapping("/white-label/{tenantId}/activate")
    public ResponseEntity<Map<String, Object>> activateAgencyDomain(@PathVariable Long tenantId) {
        WhiteLabelSettings settings = whiteLabelSettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("No white-label settings for this agency"));
        if (!"DNS_VERIFIED".equals(settings.getDomainStatus())) {
            throw new IllegalStateException(
                    "Domain must be DNS_VERIFIED before activation — current status: " + settings.getDomainStatus());
        }
        settings.setDomainStatus("ACTIVE");
        whiteLabelSettingsRepository.save(settings);
        return ResponseEntity.ok(Map.of("success", true, "message",
                "Domain marked ACTIVE. Confirm the reverse proxy/SSL for this domain is actually deployed — see docs/custom-domain-runbook.md."));
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

    @PostMapping("/agencies/{id}/custom-price")
    public ResponseEntity<Map<String, Object>> setCustomPrice(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        Object priceValue = body.get("monthlyPrice");
        if (priceValue == null) {
            t.setCustomMonthlyPrice(null);
        } else {
            t.setCustomMonthlyPrice(new BigDecimal(priceValue.toString()));
        }
        Object note = body.get("note");
        t.setCustomPriceNote(note == null ? null : note.toString());
        tenantRepository.save(t);
        return ResponseEntity.ok(Map.of("success", true, "message", "Custom price updated"));
    }

    // ── Block / Unblock / Suspend / Reactivate ──────────────────────────────────
    // Explicit, semantically-named actions on top of the generic status field —
    // each writes its own audit log entry distinct from a raw status PATCH.

    @PatchMapping("/agencies/{id}/block")
    public ResponseEntity<Map<String, Object>> blockAgency(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        t.setStatus("BLOCKED");
        tenantRepository.save(t);
        logAgencyAction(t, "AGENCY_BLOCKED", body == null ? null : String.valueOf(body.getOrDefault("reason", "")));
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency blocked. It cannot create contracts, reservations, or users until unblocked."));
    }

    @PatchMapping("/agencies/{id}/unblock")
    public ResponseEntity<Map<String, Object>> unblockAgency(@PathVariable Long id) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        t.setStatus("ACTIVE");
        tenantRepository.save(t);
        logAgencyAction(t, "AGENCY_UNBLOCKED", null);
        return ResponseEntity.ok(Map.of("success", true, "message", "Agency unblocked"));
    }

    @PatchMapping("/agencies/{id}/suspend-subscription")
    public ResponseEntity<Map<String, Object>> suspendSubscription(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        // status must move to SUSPENDED too (not just the boolean flag) — otherwise
        // repairSubscriptionState's drift-correction logic will silently flip
        // subscriptionActive back to true the next time /api/subscriptions/status
        // is loaded, undoing this suspension invisibly.
        t.setStatus("SUSPENDED");
        t.setSubscriptionActive(false);
        tenantRepository.save(t);
        logAgencyAction(t, "SUBSCRIPTION_SUSPENDED", body == null ? null : String.valueOf(body.getOrDefault("reason", "")));
        return ResponseEntity.ok(Map.of("success", true, "message", "Subscription suspended. Agency can still log in but cannot perform paid actions."));
    }

    @PatchMapping("/agencies/{id}/reactivate-subscription")
    public ResponseEntity<Map<String, Object>> reactivateSubscription(@PathVariable Long id) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        if ("SUSPENDED".equalsIgnoreCase(t.getStatus())) {
            t.setStatus("ACTIVE");
        }
        t.setSubscriptionActive(true);
        if (t.getSubscriptionEndDate() != null && LocalDate.now().isAfter(t.getSubscriptionEndDate())) {
            t.setSubscriptionEndDate(LocalDate.now().plusMonths(1));
        }
        tenantRepository.save(t);
        logAgencyAction(t, "SUBSCRIPTION_REACTIVATED", null);
        return ResponseEntity.ok(Map.of("success", true, "message", "Subscription reactivated"));
    }

    // ── Manual free access / plan override ──────────────────────────────────────

    @PostMapping("/agencies/{id}/free-access")
    public ResponseEntity<Map<String, Object>> grantFreeAccess(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        Object daysValue = body.get("days");
        if (daysValue == null) {
            throw new IllegalArgumentException("days is required");
        }
        int days = Integer.parseInt(daysValue.toString());
        if (days <= 0) {
            throw new IllegalArgumentException("days must be greater than 0");
        }
        String reason = String.valueOf(body.getOrDefault("reason", "Free access granted by Innovax Technologies"));
        t.setFreeAccessUntil(LocalDate.now().plusDays(days));
        t.setFreeAccessReason(reason);
        tenantRepository.save(t);
        logAgencyAction(t, "FREE_ACCESS_GRANTED", reason + " (until " + t.getFreeAccessUntil() + ")");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Free access granted until " + t.getFreeAccessUntil(),
                "data", Map.of("freeAccessUntil", t.getFreeAccessUntil(), "freeAccessReason", reason)));
    }

    @DeleteMapping("/agencies/{id}/free-access")
    public ResponseEntity<Map<String, Object>> removeFreeAccess(@PathVariable Long id) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        t.setFreeAccessUntil(null);
        t.setFreeAccessReason(null);
        tenantRepository.save(t);
        logAgencyAction(t, "FREE_ACCESS_REMOVED", null);
        return ResponseEntity.ok(Map.of("success", true, "message", "Free access override removed"));
    }

    // ── Balance / credits ────────────────────────────────────────────────────────

    @GetMapping("/agencies/{id}/balance")
    public ResponseEntity<Map<String, Object>> getAgencyBalance(@PathVariable Long id) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        BigDecimal totalPaid = paymentRepository.findAll().stream()
                .filter(p -> p.getType() == PaymentType.SUBSCRIPTION && p.getStatus() == PaymentStatus.PAID
                        && p.getTenant() != null && id.equals(p.getTenant().getId()))
                .map(Payment::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Agency balance loaded",
                "data", Map.of(
                        "agencyId", id,
                        "currentBalance", t.getBalance() == null ? BigDecimal.ZERO : t.getBalance(),
                        "totalPaid", totalPaid,
                        "currency", "MAD")));
    }

    @GetMapping("/agencies/{id}/balance/transactions")
    public ResponseEntity<Map<String, Object>> getAgencyBalanceTransactions(@PathVariable Long id) {
        List<Map<String, Object>> transactions = balanceTransactionRepository.findAllByTenantIdOrderByCreatedAtDesc(id).stream()
                .map(this::balanceTransactionResponse)
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "message", "Balance history loaded", "data", transactions));
    }

    @PostMapping("/agencies/{id}/balance/credit")
    public ResponseEntity<Map<String, Object>> creditAgencyBalance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(applyBalanceChange(id, body, true, "CREDIT"));
    }

    @PostMapping("/agencies/{id}/balance/debit")
    public ResponseEntity<Map<String, Object>> debitAgencyBalance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(applyBalanceChange(id, body, false, "DEBIT"));
    }

    private Map<String, Object> applyBalanceChange(Long id, Map<String, Object> body, boolean credit, String defaultType) {
        Tenant t = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Agency not found"));
        Object amountValue = body.get("amount");
        if (amountValue == null) {
            throw new IllegalArgumentException("amount is required");
        }
        BigDecimal amount = new BigDecimal(amountValue.toString()).abs();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
        Object reasonValue = body.get("reason");
        if (reasonValue == null || reasonValue.toString().isBlank()) {
            throw new IllegalArgumentException("reason is required for every balance change");
        }
        String reason = reasonValue.toString();
        String reference = body.get("reference") == null ? null : body.get("reference").toString();
        AgencyBalanceTransaction.Type type = AgencyBalanceTransaction.Type.valueOf(
                String.valueOf(body.getOrDefault("type", defaultType)).toUpperCase(Locale.ROOT));

        BigDecimal current = t.getBalance() == null ? BigDecimal.ZERO : t.getBalance();
        BigDecimal updated = credit ? current.add(amount) : current.subtract(amount);
        t.setBalance(updated);
        tenantRepository.save(t);

        AgencyBalanceTransaction transaction = balanceTransactionRepository.save(AgencyBalanceTransaction.builder()
                .tenant(t)
                .type(type)
                .amount(credit ? amount : amount.negate())
                .balanceAfter(updated)
                .reason(reason)
                .reference(reference)
                .createdBy(currentSuperAdminEmail())
                .build());

        logAgencyAction(t, credit ? "BALANCE_CREDITED" : "BALANCE_DEBITED", reason + " (" + amount + ")");

        return Map.of(
                "success", true,
                "message", (credit ? "Credited " : "Debited ") + amount + " MAD",
                "data", balanceTransactionResponse(transaction));
    }

    private Map<String, Object> balanceTransactionResponse(AgencyBalanceTransaction transaction) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", transaction.getId());
        map.put("type", transaction.getType());
        map.put("amount", transaction.getAmount());
        map.put("balanceAfter", transaction.getBalanceAfter());
        map.put("reason", transaction.getReason());
        map.put("reference", transaction.getReference());
        map.put("createdBy", transaction.getCreatedBy());
        map.put("createdAt", transaction.getCreatedAt());
        return map;
    }

    private String currentSuperAdminEmail() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return principal instanceof User user ? user.getEmail() : "system";
    }

    private void logAgencyAction(Tenant tenant, String action, String description) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType("TENANT_" + tenant.getId())
                .entityId(tenant.getId())
                .description(description)
                .performedBy(currentSuperAdminEmail())
                .tenantId(tenant.getId())
                .isSuccess(true)
                .build());
    }

    /** Same as {@link #logAgencyAction}, but for platform-wide settings with no owning tenant. */
    private void logPlatformAction(String action, String description) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .entityType("PLATFORM")
                .description(description)
                .performedBy(currentSuperAdminEmail())
                .isSuccess(true)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 13. PROMO CODES
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/promo-codes")
    public ResponseEntity<Map<String, Object>> getPromoCodes() {
        List<PromoCode> codes = promoCodeRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
        List<Map<String, Object>> list = codes.stream().map(pc -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pc.getId());
            m.put("code", pc.getCode());
            m.put("promotionName", pc.getPromotionName());
            m.put("description", pc.getDescription());
            m.put("discountType", pc.getDiscountType());
            m.put("discountValue", pc.getDiscountValue());
            m.put("currency", pc.getCurrency() != null ? pc.getCurrency() : "MAD");
            m.put("maxUses", pc.getMaxUses());
            m.put("usedCount", pc.getUsedCount() != null ? pc.getUsedCount() : 0);
            m.put("maxUsesPerAgency", pc.getMaxUsesPerAgency());
            m.put("validFrom", pc.getValidFrom());
            m.put("validTo", pc.getValidTo());
            m.put("applicablePlans", pc.getApplicablePlans());
            m.put("billingCycle", pc.getBillingCycle());
            m.put("minimumAmount", pc.getMinimumAmount());
            m.put("firstTimeOnly", Boolean.TRUE.equals(pc.getFirstTimeOnly()));
            m.put("appliesToAllPlans", Boolean.TRUE.equals(pc.getAppliesToAllPlans()));
            m.put("isActive", Boolean.TRUE.equals(pc.getIsActive()));
            m.put("promotionType", pc.getPromotionType());
            m.put("freeMonths", pc.getFreeMonths());
            m.put("createdAt", pc.getCreatedAt());
            long totalRedemptions = promoCodeRedemptionRepository.countByPromoCodeIdAndStatus(pc.getId(), "USED");
            m.put("redemptionCount", totalRedemptions);
            List<Map<String, Object>> planLinks = promoCodePlanLinkRepository
                    .findAllByPromoCodeId(pc.getId()).stream().map(link -> {
                        Map<String, Object> lm = new LinkedHashMap<>();
                        lm.put("id", link.getId());
                        lm.put("planCode", link.getPlanCode());
                        lm.put("billingCycle", link.getBillingCycle());
                        lm.put("whopCheckoutUrlOverride", link.getWhopCheckoutUrlOverride());
                        lm.put("active", Boolean.TRUE.equals(link.getActive()));
                        return lm;
                    }).toList();
            m.put("planLinks", planLinks);
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("success", true, "data", list));
    }

    @GetMapping("/promo-codes/{id}")
    public ResponseEntity<Map<String, Object>> getPromoCode(@PathVariable Long id) {
        PromoCode pc = promoCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found"));
        if (Boolean.TRUE.equals(pc.getDeleted())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true, "data", pc));
    }

    @PostMapping("/promo-codes")
    public ResponseEntity<Map<String, Object>> createPromoCode(@RequestBody Map<String, Object> body) {
        String code = Objects.toString(body.get("code"), "").trim().toUpperCase(Locale.ROOT);
        if (code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Code is required."));
        }
        if (promoCodeRepository.existsByCode(code)) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "errorCode", "PROMO_CODE_ALREADY_EXISTS", "message", "Promo code '" + code + "' already exists."));
        }
        PromoCode pc = buildPromoFromBody(new PromoCode(), body);
        pc.setCode(code);
        PromoCode saved = promoCodeRepository.save(pc);
        savePlanLinksFromBody(saved, body);
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("id", saved.getId(), "code", saved.getCode())));
    }

    @PutMapping("/promo-codes/{id}")
    public ResponseEntity<Map<String, Object>> updatePromoCode(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PromoCode pc = promoCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found"));
        String newCode = body.containsKey("code")
                ? Objects.toString(body.get("code"), "").trim().toUpperCase(Locale.ROOT) : null;
        if (newCode != null && !newCode.isBlank() && !newCode.equals(pc.getCode())) {
            if (promoCodeRepository.existsByCodeAndIdNot(newCode, id)) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "errorCode", "PROMO_CODE_ALREADY_EXISTS", "message", "Code '" + newCode + "' is already used by another promo."));
            }
            pc.setCode(newCode);
        }
        buildPromoFromBody(pc, body);
        PromoCode saved = promoCodeRepository.save(pc);
        if (body.containsKey("planLinks")) savePlanLinksFromBody(saved, body);
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("id", saved.getId())));
    }

    @PatchMapping("/promo-codes/{id}/activate")
    public ResponseEntity<Map<String, Object>> activatePromoCode(@PathVariable Long id) {
        PromoCode pc = promoCodeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
        pc.setIsActive(true);
        promoCodeRepository.save(pc);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/promo-codes/{id}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivatePromoCode(@PathVariable Long id) {
        PromoCode pc = promoCodeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
        pc.setIsActive(false);
        promoCodeRepository.save(pc);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/promo-codes/{id}")
    public ResponseEntity<Map<String, Object>> deletePromoCode(@PathVariable Long id) {
        PromoCode pc = promoCodeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
        pc.setDeleted(true);
        pc.setIsActive(false);
        promoCodeRepository.save(pc);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/promo-codes/{id}/redemptions")
    public ResponseEntity<Map<String, Object>> getPromoRedemptions(@PathVariable Long id) {
        List<PromoCodeRedemption> redemptions = promoCodeRedemptionRepository
                .findAllByPromoCodeIdOrderByRedeemedAtDesc(id);
        List<Map<String, Object>> list = redemptions.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("agencyId", r.getTenant() != null ? r.getTenant().getId() : null);
            m.put("agencyName", r.getTenant() != null ? r.getTenant().getName() : null);
            m.put("planCode", r.getPlanCode());
            m.put("billingCycle", r.getBillingCycle());
            m.put("originalPrice", r.getOriginalPrice());
            m.put("discountAmount", r.getDiscountAmount());
            m.put("finalPrice", r.getFinalPrice());
            m.put("status", r.getStatus());
            m.put("redeemedAt", r.getRedeemedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("success", true, "data", list));
    }

    /** Upsert plan links (discounted Whop URLs) for a promo code. */
    @PostMapping("/promo-codes/{id}/plan-links")
    public ResponseEntity<Map<String, Object>> savePromoCodePlanLinks(
            @PathVariable Long id, @RequestBody List<Map<String, Object>> links) {
        PromoCode pc = promoCodeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
        promoCodePlanLinkRepository.deleteAllByPromoCodeId(id);
        for (Map<String, Object> link : links) {
            String planCode = Objects.toString(link.get("planCode"), "").trim().toUpperCase(Locale.ROOT);
            String cycle = Objects.toString(link.get("billingCycle"), "MONTHLY").trim().toUpperCase(Locale.ROOT);
            String url = link.get("whopCheckoutUrlOverride") != null
                    ? link.get("whopCheckoutUrlOverride").toString().trim() : null;
            if (planCode.isBlank()) continue;
            PromoCodePlanLink pl = PromoCodePlanLink.builder()
                    .promoCode(pc).planCode(planCode).billingCycle(cycle)
                    .whopCheckoutUrlOverride(url != null && url.isBlank() ? null : url)
                    .active(true).build();
            promoCodePlanLinkRepository.save(pl);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    private PromoCode buildPromoFromBody(PromoCode pc, Map<String, Object> body) {
        if (body.containsKey("promotionName")) pc.setPromotionName(Objects.toString(body.get("promotionName"), null));
        if (body.containsKey("description")) pc.setDescription(Objects.toString(body.get("description"), null));
        if (body.containsKey("discountType")) pc.setDiscountType(Objects.toString(body.get("discountType"), null));
        if (body.containsKey("discountValue") && body.get("discountValue") != null) {
            pc.setDiscountValue(new BigDecimal(body.get("discountValue").toString()));
        }
        if (body.containsKey("maxUses")) {
            pc.setMaxUses(body.get("maxUses") != null ? Integer.valueOf(body.get("maxUses").toString()) : null);
        }
        if (body.containsKey("maxUsesPerAgency")) {
            pc.setMaxUsesPerAgency(body.get("maxUsesPerAgency") != null ? Integer.valueOf(body.get("maxUsesPerAgency").toString()) : null);
        }
        if (body.containsKey("validFrom")) {
            pc.setValidFrom(body.get("validFrom") != null ? LocalDate.parse(body.get("validFrom").toString()) : null);
        }
        if (body.containsKey("validTo")) {
            pc.setValidTo(body.get("validTo") != null ? LocalDate.parse(body.get("validTo").toString()) : null);
        }
        if (body.containsKey("applicablePlans")) pc.setApplicablePlans(Objects.toString(body.get("applicablePlans"), null));
        if (body.containsKey("billingCycle")) pc.setBillingCycle(Objects.toString(body.get("billingCycle"), "BOTH"));
        if (body.containsKey("minimumAmount")) {
            pc.setMinimumAmount(body.get("minimumAmount") != null ? new BigDecimal(body.get("minimumAmount").toString()) : null);
        }
        if (body.containsKey("firstTimeOnly")) pc.setFirstTimeOnly(Boolean.parseBoolean(body.get("firstTimeOnly").toString()));
        if (body.containsKey("appliesToAllPlans")) pc.setAppliesToAllPlans(Boolean.parseBoolean(body.get("appliesToAllPlans").toString()));
        if (body.containsKey("isActive")) pc.setIsActive(Boolean.parseBoolean(body.get("isActive").toString()));
        if (body.containsKey("promotionType")) pc.setPromotionType(Objects.toString(body.get("promotionType"), null));
        if (body.containsKey("freeMonths")) {
            pc.setFreeMonths(body.get("freeMonths") != null ? Integer.valueOf(body.get("freeMonths").toString()) : null);
        }
        if (body.containsKey("currency")) pc.setCurrency(Objects.toString(body.get("currency"), "MAD"));
        return pc;
    }

    @SuppressWarnings("unchecked")
    private void savePlanLinksFromBody(PromoCode saved, Map<String, Object> body) {
        if (!(body.get("planLinks") instanceof List)) return;
        List<Map<String, Object>> links = (List<Map<String, Object>>) body.get("planLinks");
        promoCodePlanLinkRepository.deleteAllByPromoCodeId(saved.getId());
        for (Map<String, Object> link : links) {
            String planCode = Objects.toString(link.get("planCode"), "").trim().toUpperCase(Locale.ROOT);
            String cycle = Objects.toString(link.get("billingCycle"), "MONTHLY").trim().toUpperCase(Locale.ROOT);
            String url = link.get("whopCheckoutUrlOverride") != null
                    ? link.get("whopCheckoutUrlOverride").toString().trim() : null;
            if (planCode.isBlank()) continue;
            PromoCodePlanLink pl = PromoCodePlanLink.builder()
                    .promoCode(saved).planCode(planCode).billingCycle(cycle)
                    .whopCheckoutUrlOverride(url != null && url.isBlank() ? null : url)
                    .active(true).build();
            promoCodePlanLinkRepository.save(pl);
        }
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
    // 14. EMAIL CENTER — TEMPLATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /** List all templates (including inactive). */
    @GetMapping("/email/templates")
    public ResponseEntity<List<EmailTemplate>> getEmailTemplates() {
        return ResponseEntity.ok(emailTemplateRepository.findAll());
    }

    /** Get a single template by id. */
    @GetMapping("/email/templates/{id}")
    public ResponseEntity<EmailTemplate> getEmailTemplate(@PathVariable Long id) {
        return emailTemplateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Returns all distinct template type category strings. */
    @GetMapping("/email/templates/types")
    public ResponseEntity<List<String>> getEmailTemplateTypes() {
        return ResponseEntity.ok(emailTemplateService.getAllTypes());
    }

    /** Returns variable definitions for a template key. */
    @GetMapping("/email/templates/variables")
    public ResponseEntity<List<Map<String, String>>> getEmailTemplateVariables(
            @RequestParam(required = false) String templateKey) {
        return ResponseEntity.ok(emailTemplateService.getVariables(templateKey != null ? templateKey : ""));
    }

    /** Create a new template (non-system). */
    @PostMapping("/email/templates")
    public ResponseEntity<EmailTemplate> createEmailTemplate(@RequestBody Map<String, Object> body) {
        EmailTemplate t = EmailTemplate.builder()
                .name(toStr(body.get("name")))
                .templateKey(toStr(body.get("templateKey")))
                .type(toStr(body.get("type")))
                .language(body.containsKey("language") ? toStr(body.get("language")) : "EN")
                .subject(toStr(body.get("subject")))
                .bodyHtml(emailTemplateService.sanitizeHtml(toStr(body.get("bodyHtml"))))
                .bodyText(toStr(body.get("bodyText")))
                .isActive(body.containsKey("isActive") ? toBool(body.get("isActive")) : true)
                .systemDefault(false)
                .createdBy("SUPER_ADMIN")
                .build();
        return ResponseEntity.ok(emailTemplateRepository.save(t));
    }

    /** Update an existing template. System defaults keep their templateKey. */
    @PutMapping("/email/templates/{id}")
    public ResponseEntity<EmailTemplate> updateEmailTemplate(
            @PathVariable Long id, @RequestBody Map<String, Object> updates) {
        EmailTemplate et = emailTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        if (updates.containsKey("name"))     et.setName(toStr(updates.get("name")));
        if (updates.containsKey("type"))     et.setType(toStr(updates.get("type")));
        if (updates.containsKey("language")) et.setLanguage(toStr(updates.get("language")));
        if (updates.containsKey("subject"))  et.setSubject(toStr(updates.get("subject")));
        if (updates.containsKey("bodyHtml")) et.setBodyHtml(emailTemplateService.sanitizeHtml(toStr(updates.get("bodyHtml"))));
        if (updates.containsKey("bodyText")) et.setBodyText(toStr(updates.get("bodyText")));
        if (updates.containsKey("isActive")) et.setIsActive(toBool(updates.get("isActive")));
        et.setUpdatedBy("SUPER_ADMIN");
        return ResponseEntity.ok(emailTemplateRepository.save(et));
    }

    /**
     * Delete a template. System defaults cannot be permanently deleted —
     * they are deactivated instead.
     */
    @DeleteMapping("/email/templates/{id}")
    public ResponseEntity<Map<String, Object>> deleteEmailTemplate(@PathVariable Long id) {
        EmailTemplate template = emailTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        if (Boolean.TRUE.equals(template.getSystemDefault())) {
            template.setIsActive(false);
            emailTemplateRepository.save(template);
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "System default template deactivated (cannot be permanently deleted)"));
        }
        emailTemplateRepository.delete(template);
        return ResponseEntity.ok(Map.of("success", true, "message", "Template deleted successfully"));
    }

    /** Duplicate a template (creates a non-system copy). */
    @PostMapping("/email/templates/{id}/duplicate")
    public ResponseEntity<EmailTemplate> duplicateEmailTemplate(@PathVariable Long id) {
        EmailTemplate src = emailTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        EmailTemplate copy = EmailTemplate.builder()
                .name(src.getName() + " (Copy)")
                .templateKey(null)          // copies are not system keys
                .type(src.getType())
                .language(src.getLanguage())
                .subject(src.getSubject())
                .bodyHtml(src.getBodyHtml())
                .bodyText(src.getBodyText())
                .isActive(false)            // start inactive until reviewed
                .systemDefault(false)
                .createdBy("SUPER_ADMIN")
                .build();
        return ResponseEntity.ok(emailTemplateRepository.save(copy));
    }

    /** Reset a system default template to its built-in content. */
    @PostMapping("/email/templates/{id}/reset-default")
    public ResponseEntity<Map<String, Object>> resetEmailTemplate(@PathVariable Long id) {
        boolean ok = emailTemplateService.resetToDefault(id);
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "message", "Template could not be reset (no default exists for this key)"));
        }
        EmailTemplate updated = emailTemplateRepository.findById(id).orElseThrow();
        return ResponseEntity.ok(Map.of("success", true, "message", "Template reset to default", "data", updated));
    }

    /**
     * Send a test email using a specific template.
     * Uses the platform SMTP; variables may be supplied in the request body.
     */
    @PostMapping("/email/templates/{id}/test")
    public ResponseEntity<Map<String, Object>> testSendTemplate(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        EmailTemplate template = emailTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        String to = toStr(body.get("to"));
        if (to == null || to.isBlank()) throw new IllegalArgumentException("Recipient email required");
        if (!to.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("Invalid recipient email");

        // Build variable map from request
        Map<String, String> vars = new java.util.HashMap<>();
        Object rawVars = body.get("variables");
        if (rawVars instanceof Map<?,?> m) {
            m.forEach((k, v) -> { if (k != null && v != null) vars.put(k.toString(), v.toString()); });
        }

        // If templateKey present, render via service (includes enrichment); otherwise use raw body
        String renderedSubject;
        String renderedHtml;
        String renderedPlain;
        if (template.getTemplateKey() != null) {
            var rendered = emailTemplateService.render(
                    template.getTemplateKey(),
                    template.getLanguage() != null ? template.getLanguage() : "EN",
                    vars);
            if (rendered.isPresent()) {
                renderedSubject = rendered.get().subject();
                renderedHtml    = rendered.get().htmlBody();
                renderedPlain   = rendered.get().plainBody();
            } else {
                renderedSubject = template.getSubject();
                renderedHtml    = template.getBodyHtml();
                renderedPlain   = template.getBodyText();
            }
        } else {
            renderedSubject = template.getSubject();
            renderedHtml    = template.getBodyHtml();
            renderedPlain   = template.getBodyText();
        }

        com.carrental.service.SmtpMailService.SmtpResult result =
                smtpMailService.sendPlatform(to, renderedSubject,
                        renderedHtml != null ? renderedHtml : "",
                        renderedPlain != null ? renderedPlain : "");

        // Log it
        com.carrental.entity.EmailLog logEntry = new com.carrental.entity.EmailLog();
        logEntry.setRecipient(to);
        logEntry.setSubject(renderedSubject);
        logEntry.setEmailType("TEMPLATE_TEST");
        logEntry.setTemplateName(template.getName());
        logEntry.setStatus(result.sent() ? "SENT" : "FAILED");
        logEntry.setErrorCode(result.sent() ? null : "SMTP_SEND_FAILED");
        logEntry.setErrorMessage(result.sent() ? null : result.errorMessage());
        emailLogRepository.save(logEntry);

        if (result.sent()) {
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "Test email sent successfully to " + to));
        }
        return ResponseEntity.ok(Map.of("success", false,
                "message", "Failed to send test email: " + result.errorMessage()));
    }

    // ── Dedicated SMTP settings endpoints (Super Admin only) ─────────────────

    @GetMapping("/email/settings")
    public ResponseEntity<Map<String, Object>> getSmtpSettings() {
        PlatformSettings ps = platformSettingsService.getOrCreateSingleton();
        Map<String, Object> result = new LinkedHashMap<>();
        // Always return non-null strings so the frontend never receives null for input values
        result.put("smtpProvider",      ps.getSmtpProvider() != null ? ps.getSmtpProvider() : "ZOHO");
        result.put("smtpHost",         ps.getSmtpHost() != null ? ps.getSmtpHost() : "");
        result.put("smtpPort",         ps.getSmtpPort() != null ? ps.getSmtpPort() : 587);
        result.put("smtpUsername",     ps.getSmtpUsername() != null ? ps.getSmtpUsername() : "");
        result.put("hasPassword",      ps.getSmtpPasswordEncrypted() != null && !ps.getSmtpPasswordEncrypted().isBlank());
        result.put("smtpUseTls",       ps.getSmtpUseTls() == null ? true : ps.getSmtpUseTls());
        result.put("smtpSslEnabled",   Boolean.TRUE.equals(ps.getSmtpSslEnabled()));
        result.put("smtpEnabled",      ps.getSmtpEnabled() == null ? false : ps.getSmtpEnabled());
        result.put("fromEmail",        ps.getFromEmail() != null ? ps.getFromEmail() : "");
        result.put("fromName",         ps.getFromName() != null ? ps.getFromName() : "");
        result.put("smtpReplyTo",      ps.getSmtpReplyTo() != null ? ps.getSmtpReplyTo() : "");
        result.put("lastTestStatus",   ps.getLastSmtpTestStatus());
        result.put("lastTestAt",       ps.getLastSmtpTestAt());
        result.put("lastTestErrorCode", ps.getLastSmtpTestErrorCode());
        // Email always sends via the ZeptoMail HTTP API now (see HttpEmailProvider) —
        // SMTP is never attempted in production. Config is env-var-driven on Railway
        // (ZEPTOMAIL_API_TOKEN, EMAIL_FROM_EMAIL), never stored in this table; the
        // smtp* fields above are kept read-only for historical reference only.
        result.put("activeEmailProvider", smtpMailService.activeProvider());
        result.put("emailProviderConfigured", smtpMailService.isPlatformConfigured());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/email/settings")
    public ResponseEntity<Map<String, Object>> updateSmtpSettings(@RequestBody Map<String, Object> updates) {
        PlatformSettings ps = platformSettingsService.getOrCreateSingleton();

        String host       = updates.containsKey("smtpHost")     ? trimStr(updates.get("smtpHost"))     : ps.getSmtpHost();
        Integer port      = updates.containsKey("smtpPort")     ? toInt(updates.get("smtpPort"))       : ps.getSmtpPort();
        String username    = updates.containsKey("smtpUsername") ? trimStr(updates.get("smtpUsername")) : ps.getSmtpUsername();
        String fromEmail  = updates.containsKey("fromEmail")    ? trimStr(updates.get("fromEmail"))    : ps.getFromEmail();
        Boolean enabled   = updates.containsKey("smtpEnabled")  ? toBool(updates.get("smtpEnabled"))   : ps.getSmtpEnabled();
        boolean useTls    = updates.containsKey("smtpUseTls")   ? Boolean.TRUE.equals(toBool(updates.get("smtpUseTls"))) : (ps.getSmtpUseTls() == null || ps.getSmtpUseTls());
        boolean useSsl    = updates.containsKey("smtpSslEnabled") ? Boolean.TRUE.equals(toBool(updates.get("smtpSslEnabled"))) : Boolean.TRUE.equals(ps.getSmtpSslEnabled());

        Map<String, Object> validationError = validateSmtpSettings(host, port, username, fromEmail, enabled, useTls, useSsl);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }

        if (updates.containsKey("smtpProvider")) ps.setSmtpProvider(trimStr(updates.get("smtpProvider")));
        if (updates.containsKey("smtpHost"))     ps.setSmtpHost(host);
        if (updates.containsKey("smtpPort"))     ps.setSmtpPort(port);
        if (updates.containsKey("smtpUsername")) ps.setSmtpUsername(username);
        boolean newPasswordProvided = false;
        if (updates.containsKey("smtpPassword")) {
            String pw = toStr(updates.get("smtpPassword"));
            if (pw != null) {
                // Normalize: remove accidental whitespace/line breaks that break auth
                pw = pw.strip().replaceAll("[\\r\\n\\t]+", "");
                if (!pw.isBlank()) {
                    ps.setSmtpPasswordEncrypted(encryptionUtil.encrypt(pw));
                    newPasswordProvided = true;
                    // Never log the password value
                }
            }
        }
        if (updates.containsKey("smtpUseTls"))       ps.setSmtpUseTls(useTls);
        if (updates.containsKey("smtpSslEnabled"))   ps.setSmtpSslEnabled(useSsl);
        if (updates.containsKey("smtpEnabled"))      ps.setSmtpEnabled(enabled);
        if (updates.containsKey("fromEmail"))        ps.setFromEmail(fromEmail);
        if (updates.containsKey("fromName"))         ps.setFromName(trimStr(updates.get("fromName")));
        if (updates.containsKey("smtpReplyTo"))      ps.setSmtpReplyTo(trimStr(updates.get("smtpReplyTo")));
        platformSettingsRepository.save(ps);
        boolean passwordStored = ps.getSmtpPasswordEncrypted() != null && !ps.getSmtpPasswordEncrypted().isBlank();
        log.debug("[SMTP_SAVE_DEBUG] host={} port={} username={} fromEmail={} passwordProvided={} passwordStored={}",
                ps.getSmtpHost(), ps.getSmtpPort(), ps.getSmtpUsername(), ps.getFromEmail(),
                newPasswordProvided, passwordStored);
        logPlatformAction("SMTP_SETTINGS_UPDATED", "Updated SMTP settings: host=" + ps.getSmtpHost()
                + " port=" + ps.getSmtpPort() + " enabled=" + ps.getSmtpEnabled()
                + " tls=" + ps.getSmtpUseTls() + " ssl=" + ps.getSmtpSslEnabled()
                + " passwordChanged=" + newPasswordProvided);

        // Re-read from the DB to confirm persistence before reporting success, rather than
        // trusting the in-memory entity we just saved.
        return getSmtpSettings();
    }

    /** Returns a 400-shaped error map if the resulting SMTP config would be invalid, else null. */
    private Map<String, Object> validateSmtpSettings(String host, Integer port, String username, String fromEmail,
                                                       Boolean enabled, boolean useTls, boolean useSsl) {
        if (Boolean.TRUE.equals(enabled)) {
            if (!org.springframework.util.StringUtils.hasText(host)) {
                return Map.of("success", false, "errorCode", "SMTP_HOST_MISSING",
                        "message", "SMTP host is required.");
            }
            if (!org.springframework.util.StringUtils.hasText(username)) {
                return Map.of("success", false, "errorCode", "SMTP_USERNAME_MISSING",
                        "message", "SMTP username is required.");
            }
        }
        if (port != null && (port < 1 || port > 65535)) {
            return Map.of("success", false, "errorCode", "SMTP_PORT_INVALID",
                    "message", "SMTP port must be between 1 and 65535.");
        }
        if (org.springframework.util.StringUtils.hasText(fromEmail)
                && !fromEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) {
            return Map.of("success", false, "errorCode", "SMTP_FROM_EMAIL_INVALID",
                    "message", "From Email must be a valid email address.");
        }
        if (useTls && useSsl) {
            return Map.of("success", false, "errorCode", "SMTP_TLS_SSL_CONFLICT",
                    "message", "STARTTLS and SSL cannot both be enabled for the same connection.");
        }
        // The two well-known SMTP ports each imply exactly one encryption mode —
        // implicit SSL on 465 (no STARTTLS negotiation at all), STARTTLS on 587.
        // Saving the wrong combination for these specific ports produces exactly
        // the "TCP OK, TLS FAILED" or a connection that never completes its
        // handshake failure this task is about, so it's rejected before saving
        // rather than only being caught later by running Diagnose. Non-standard
        // ports (a provider other than the two well-known ones) aren't forced
        // into this pairing — only 465/587 have one universally correct answer.
        if (port != null && port == 465 && !useSsl) {
            return Map.of("success", false, "errorCode", "SMTP_PORT_MODE_MISMATCH",
                    "message", "Port 465 requires SSL to be enabled (implicit SSL) with STARTTLS disabled.");
        }
        if (port != null && port == 587 && !useTls) {
            return Map.of("success", false, "errorCode", "SMTP_PORT_MODE_MISMATCH",
                    "message", "Port 587 requires STARTTLS to be enabled with SSL disabled.");
        }
        return null;
    }

    // ── Support Center routing settings (Super Admin only) ───────────────────

    @GetMapping("/support/settings")
    public ResponseEntity<Map<String, Object>> getSupportRoutingSettings() {
        PlatformSettings ps = platformSettingsService.getOrCreateSingleton();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contactEmail",   ps.getContactEmail() != null ? ps.getContactEmail() : "");
        result.put("supportEmail",   ps.getSupportEmail() != null ? ps.getSupportEmail() : "");
        result.put("technicalEmail", ps.getTechnicalEmail() != null ? ps.getTechnicalEmail() : "");
        result.put("billingEmail",   ps.getBillingEmail() != null ? ps.getBillingEmail() : "");
        result.put("securityEmail",  ps.getSecurityEmail() != null ? ps.getSecurityEmail() : "");
        result.put("fallbackEmail",  ps.getFallbackEmail() != null ? ps.getFallbackEmail() : "");
        return ResponseEntity.ok(result);
    }

    @PutMapping("/support/settings")
    public ResponseEntity<Map<String, Object>> updateSupportRoutingSettings(@RequestBody Map<String, Object> updates) {
        PlatformSettings ps = platformSettingsService.getOrCreateSingleton();
        if (updates.containsKey("contactEmail"))   ps.setContactEmail(trimStr(updates.get("contactEmail")));
        if (updates.containsKey("supportEmail"))   ps.setSupportEmail(trimStr(updates.get("supportEmail")));
        if (updates.containsKey("technicalEmail")) ps.setTechnicalEmail(trimStr(updates.get("technicalEmail")));
        if (updates.containsKey("billingEmail"))   ps.setBillingEmail(trimStr(updates.get("billingEmail")));
        if (updates.containsKey("securityEmail"))  ps.setSecurityEmail(trimStr(updates.get("securityEmail")));
        if (updates.containsKey("fallbackEmail"))  ps.setFallbackEmail(trimStr(updates.get("fallbackEmail")));
        platformSettingsRepository.save(ps);
        return getSupportRoutingSettings();
    }

    @PostMapping("/email/test")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody Map<String, Object> body) {
        logPlatformAction("EMAIL_TEST_EMAIL_REQUESTED", "Super Admin requested a test email");
        String recipient = toStr(body.get("to"));
        if (recipient == null || recipient.isBlank()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false); err.put("errorCode", "TEST_RECIPIENT_MISSING");
            err.put("message", "Recipient email is required.");
            return ResponseEntity.ok(err);
        }
        recipient = recipient.strip();
        // Require: local@domain.tld with domain having at least 2-char TLD
        if (!recipient.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false); err.put("errorCode", "TEST_RECIPIENT_INVALID");
            err.put("message", "The test recipient address '" + recipient + "' is not a valid email. Please enter a real address.");
            return ResponseEntity.ok(err);
        }

        // ── Pre-flight config validation ──────────────────────────────────────
        // Email always sends via the ZeptoMail HTTP API (see HttpEmailProvider) —
        // configuration is env-var-driven (ZEPTOMAIL_API_TOKEN, EMAIL_FROM_EMAIL),
        // set on Railway, never stored in the database.
        if (!smtpMailService.isPlatformConfigured()) {
            return ResponseEntity.ok(Map.of("success", false, "errorCode", "EMAIL_CONFIGURATION_MISSING",
                    "message", "ZeptoMail is not configured. Set ZEPTOMAIL_API_TOKEN and EMAIL_FROM_EMAIL as environment variables on Railway."));
        }

        log.debug("[EMAIL_SEND_TEST_DEBUG] provider=ZEPTOMAIL testRecipient={} sendAttempted=true", recipient);

        // ── Attempt send ──────────────────────────────────────────────────────
        com.carrental.service.SmtpMailService.SmtpResult result = platformEmailService.sendTestEmail(recipient);

        log.debug("[EMAIL_SEND_TEST_DEBUG] provider=ZEPTOMAIL testRecipient={} sendAttempted=true exceptionClass={} exceptionMessage={} errorCode={}",
                recipient, result.exceptionClass(), result.errorMessage(), result.errorCode());

        PlatformSettings psCheck = platformSettingsRepository.findTopByOrderByIdAsc().orElse(null);

        if (result.sent()) {
            log.debug("[EMAIL_DIAG_DEBUG] sendResult=OK errorCode=null testRecipient={}", recipient);
            if (psCheck != null) {
                psCheck.setLastSmtpTestStatus("SENT");
                psCheck.setLastSmtpTestAt(java.time.LocalDateTime.now());
                psCheck.setLastSmtpTestErrorCode(null);
                platformSettingsRepository.save(psCheck);
            }
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "Test email sent successfully to " + recipient));
        }

        // ── Use typed error code from HttpEmailProvider/SmtpMailService ───────
        String errorCode = result.errorCode() != null ? result.errorCode() : "EMAIL_SEND_FAILED";
        log.warn("[EMAIL_DIAG_DEBUG] sendResult=FAILED errorCode={} rawError={}", errorCode, result.errorMessage());

        String safeMessage = switch (errorCode) {
            case "EMAIL_API_AUTH_FAILED"      -> "ZeptoMail rejected the API token. Verify ZEPTOMAIL_API_TOKEN in Railway is correct and active.";
            case "EMAIL_API_RATE_LIMITED"     -> "ZeptoMail rate-limited this request. Wait a moment and try again.";
            case "EMAIL_API_PROVIDER_ERROR"   -> "ZeptoMail returned a server error. Try again shortly.";
            case "EMAIL_API_REQUEST_REJECTED" -> "ZeptoMail rejected the request. Verify the sending domain (EMAIL_FROM_EMAIL) is verified in your ZeptoMail account.";
            case "EMAIL_API_TIMEOUT"          -> "The request to ZeptoMail timed out. Try again.";
            case "EMAIL_CONFIGURATION_MISSING"-> "ZeptoMail is not fully configured. Set ZEPTOMAIL_API_TOKEN and EMAIL_FROM_EMAIL in Railway.";
            case "EMAIL_NO_RECIPIENT"         -> "The test recipient address is missing or invalid.";
            default                           -> "Test email could not be sent (" + errorCode + "). Verify ZEPTOMAIL_API_TOKEN and EMAIL_FROM_EMAIL are set correctly in Railway.";
        };

        if (psCheck != null) {
            psCheck.setLastSmtpTestStatus("FAILED");
            psCheck.setLastSmtpTestAt(java.time.LocalDateTime.now());
            psCheck.setLastSmtpTestErrorCode(errorCode);
            platformSettingsRepository.save(psCheck);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("errorCode", errorCode);
        response.put("message", safeMessage);
        return ResponseEntity.ok(response);
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
        ticketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        List<Map<String, Object>> notes = supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(id).stream()
                .filter(message -> "INTERNAL_NOTE".equals(message.getSenderType()))
                .map(this::supportNoteMap)
                .toList();
        return ResponseEntity.ok(notes);
    }

    @PostMapping("/tickets/{id}/notes")
    public ResponseEntity<Map<String, Object>> addTicketNote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        String content = toStr(body.get("content"));
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Note content is required");
        }

        SupportMessage saved = supportMessageRepository.save(SupportMessage.builder()
                .ticket(ticket)
                .senderName("Innovax Support")
                .senderType("INTERNAL_NOTE")
                .message(content.trim())
                .readAt(LocalDateTime.now())
                .build());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Note added successfully",
                "item", supportNoteMap(saved)
        ));
    }

    @GetMapping("/support/analytics")
    public ResponseEntity<Map<String, Object>> getSupportAnalytics() {
        List<SupportTicket> tickets = ticketRepository.findAll();
        double avgResolutionHours = tickets.stream()
                .filter(t -> t.getCreatedAt() != null && t.getResolvedAt() != null)
                .mapToLong(t -> ChronoUnit.MINUTES.between(t.getCreatedAt(), t.getResolvedAt()))
                .average()
                .orElse(0) / 60.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTickets", tickets.size());
        result.put("openTickets", tickets.stream().filter(t -> "OPEN".equals(t.getStatus())).count());
        result.put("resolvedTickets", tickets.stream().filter(t -> t.getResolvedAt() != null).count());
        result.put("criticalTickets", tickets.stream().filter(t -> "CRITICAL".equals(t.getPriority())).count());
        result.put("avgResolutionHours", Math.round(avgResolutionHours * 100.0) / 100.0);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> supportNoteMap(SupportMessage message) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", message.getId());
        value.put("content", message.getMessage() == null ? "" : message.getMessage());
        value.put("createdBy", message.getSenderName());
        value.put("createdAt", message.getCreatedAt());
        return value;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 16. SECURITY EXTENSIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/security/login-history")
    public ResponseEntity<List<Map<String, Object>>> getLoginHistory() {
        List<Map<String, Object>> result = loginAttemptRepository.findTop100ByOrderByAttemptedAtDesc().stream().map(l -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", l.getId());
            map.put("performedBy", l.getEmail());
            map.put("ipAddress", l.getIpAddress());
            map.put("success", l.getSuccessful());
            map.put("suspicious", l.getSuspicious());
            map.put("userAgent", l.getUserAgent());
            map.put("createdAt", l.getAttemptedAt());
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
        List<Map<String, Object>> result = loginAttemptRepository.findTop100ByOrderByAttemptedAtDesc().stream()
                .filter(l -> Boolean.FALSE.equals(l.getSuccessful())).map(l -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", l.getId());
            map.put("performedBy", l.getEmail());
            map.put("ipAddress", l.getIpAddress());
            map.put("reason", l.getFailureReason() == null ? "Invalid credentials" : l.getFailureReason());
            map.put("suspicious", l.getSuspicious());
            map.put("createdAt", l.getAttemptedAt());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 17. PLATFORM HEALTH
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getPlatformHealth() {
        return ResponseEntity.ok(systemHealthService.platformHealth());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 18. MARKETING
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/marketing/onboarding")
    public ResponseEntity<Map<String, Object>> getMarketingOnboarding() {
        return ResponseEntity.ok(marketingOnboardingData(platformSettings()));
    }

    @PutMapping("/marketing/onboarding")
    public ResponseEntity<Map<String, Object>> updateMarketingOnboarding(@RequestBody Map<String, Object> body) {
        PlatformSettings settings = platformSettings();
        Map<String, Object> data = marketingOnboardingData(settings);
        data.putAll(body);
        try {
            settings.setMarketingOnboardingJson(objectMapper.writeValueAsString(data));
            platformSettingsRepository.save(settings);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Marketing onboarding settings could not be saved");
        }
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/marketing/conversion")
    public ResponseEntity<Map<String, Object>> getMarketingConversion() {
        List<Tenant> tenants = tenantRepository.findAll();
        List<OnboardingProgress> progress = onboardingProgressRepository.findAll();
        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);

        long signupsLast30Days = tenants.stream()
                .filter(tenant -> tenant.getCreatedAt() != null && !tenant.getCreatedAt().isBefore(last30Days))
                .count();
        long completedOnboarding = progress.stream().filter(OnboardingProgress::isCompleted).count();
        long paidConversions = tenants.stream().filter(Tenant::isSubscriptionValid).count();
        double trialToPaidRate = tenants.isEmpty() ? 0 : (paidConversions * 100.0 / tenants.size());
        double avgOnboardingMinutes = progress.stream()
                .filter(OnboardingProgress::isCompleted)
                .filter(item -> item.getTenant() != null
                        && item.getTenant().getCreatedAt() != null
                        && item.getCompletedAt() != null)
                .mapToLong(item -> ChronoUnit.MINUTES.between(item.getTenant().getCreatedAt(), item.getCompletedAt()))
                .average()
                .orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("websiteVisits", 0);
        result.put("signupsStarted", signupsLast30Days);
        result.put("trialsCreated", signupsLast30Days);
        result.put("totalTrials", tenants.size());
        result.put("trialsCompleted", completedOnboarding);
        result.put("paidConversion", paidConversions);
        result.put("trialToPaidRate", Math.round(trialToPaidRate * 100.0) / 100.0);
        result.put("avgOnboardingMinutes", Math.round(avgOnboardingMinutes * 100.0) / 100.0);
        result.put("landingCTR", 0);
        result.put("trafficTrackingEnabled", false);
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 19. CONTRACTS
    // ═══════════════════════════════════════════════════════════════════════════

    private PlatformSettings platformSettings() {
        return platformSettingsService.getOrCreateSingleton();
    }

    private Map<String, Object> marketingOnboardingData(PlatformSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>(marketingOnboardingDefaults());
        String stored = settings.getMarketingOnboardingJson();
        if (stored == null || stored.isBlank()) return data;
        try {
            Map<?, ?> parsed = objectMapper.readValue(stored, Map.class);
            parsed.forEach((key, value) -> {
                if (key != null) data.put(key.toString(), value);
            });
            return data;
        } catch (JsonProcessingException exception) {
            return data;
        }
    }

    private Map<String, Object> marketingOnboardingDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("step1Title", "Welcome to Innovax");
        defaults.put("step1Description", "Let's get your agency set up in minutes.");
        defaults.put("step2Title", "Configure Your Fleet");
        defaults.put("step3Title", "Invite Your Team");
        defaults.put("completionMessage", "You're all set!");
        defaults.put("heroHeadline", "The complete car rental management platform");
        defaults.put("heroSubtitle", "Manage your fleet, bookings, and payments in one place.");
        defaults.put("ctaText", "Start Free Trial");
        defaults.put("features", "GPS Tracking\nDigital Contracts\nRevenue Analytics");
        return defaults;
    }

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
        LocalDate rangeStart = parseReportDate(startDate, LocalDate.now().minusDays(30));
        LocalDate rangeEnd = parseReportDate(endDate, LocalDate.now());
        if (rangeEnd.isBefore(rangeStart)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }

        List<Map<String, Object>> rows = switch (type) {
            case "revenue" -> revenueReportRows(rangeStart, rangeEnd);
            case "agencies" -> agencyReportRows(rangeStart, rangeEnd);
            case "gps" -> gpsReportRows(rangeStart, rangeEnd);
            case "support" -> supportReportRows(rangeStart, rangeEnd);
            case "security" -> securityReportRows(rangeStart, rangeEnd);
            default -> throw new IllegalArgumentException("Unsupported report type: " + type);
        };
        List<String> columns = reportColumns(type, rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("startDate", rangeStart);
        result.put("endDate", rangeEnd);
        result.put("generatedAt", LocalDateTime.now());
        result.put("format", "csv");
        result.put("filename", "rentcar-" + type + "-" + rangeStart + "-to-" + rangeEnd + ".csv");
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("csv", toCsv(columns, rows));
        return ResponseEntity.ok(result);
    }

    private List<Map<String, Object>> revenueReportRows(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        return paymentRepository.findAll().stream()
                .filter(payment -> isWithin(payment.getPaymentDate(), start, endExclusive))
                .map(payment -> row(
                        "paymentNumber", payment.getPaymentNumber(),
                        "agency", payment.getTenant() != null ? payment.getTenant().getName() : "",
                        "amount", payment.getAmount(),
                        "status", payment.getStatus(),
                        "type", payment.getType(),
                        "paymentDate", payment.getPaymentDate()
                ))
                .toList();
    }

    private List<Map<String, Object>> agencyReportRows(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        return tenantRepository.findAll().stream()
                .filter(tenant -> isWithin(tenant.getCreatedAt(), start, endExclusive))
                .map(tenant -> row(
                        "id", tenant.getId(),
                        "name", tenant.getName(),
                        "email", tenant.getEmail(),
                        "status", tenant.getStatus(),
                        "verificationStatus", tenant.getVerificationStatus(),
                        "planName", tenant.getPlanName(),
                        "subscriptionActive", tenant.isSubscriptionActive(),
                        "createdAt", tenant.getCreatedAt()
                ))
                .toList();
    }

    private List<Map<String, Object>> gpsReportRows(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        List<GpsAlert> alerts = gpsAlertRepository.findAll();
        return gpsSettingsRepository.findAll().stream()
                .map(settings -> {
                    Long tenantId = settings.getTenant() != null ? settings.getTenant().getId() : null;
                    long alertCount = alerts.stream()
                            .filter(alert -> isWithin(alert.getCreatedAt(), start, endExclusive))
                            .filter(alert -> tenantId != null && alert.getTenant() != null && tenantId.equals(alert.getTenant().getId()))
                            .count();
                    return row(
                            "agency", settings.getTenant() != null ? settings.getTenant().getName() : "",
                            "provider", settings.getProvider(),
                            "enabled", settings.getEnabled(),
                            "connectionStatus", settings.getConnectionStatus(),
                            "activeDevices", settings.getActiveDevices(),
                            "alertsInRange", alertCount,
                            "lastSyncAt", settings.getLastSyncAt()
                    );
                })
                .toList();
    }

    private List<Map<String, Object>> supportReportRows(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        return ticketRepository.findAll().stream()
                .filter(ticket -> isWithin(ticket.getCreatedAt(), start, endExclusive))
                .map(ticket -> row(
                        "ticketNumber", ticket.getTicketNumber(),
                        "subject", ticket.getSubject(),
                        "status", ticket.getStatus(),
                        "priority", ticket.getPriority(),
                        "createdBy", ticket.getCreatedBy(),
                        "assignedTo", ticket.getAssignedTo(),
                        "createdAt", ticket.getCreatedAt(),
                        "resolvedAt", ticket.getResolvedAt()
                ))
                .toList();
    }

    private List<Map<String, Object>> securityReportRows(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
        return loginAttemptRepository.findTop100ByOrderByAttemptedAtDesc().stream()
                .filter(attempt -> isWithin(attempt.getAttemptedAt(), start, endExclusive))
                .map(attempt -> row(
                        "email", attempt.getEmail(),
                        "ipAddress", attempt.getIpAddress(),
                        "success", attempt.getSuccessful(),
                        "suspicious", attempt.getSuspicious(),
                        "browser", attempt.getBrowser(),
                        "operatingSystem", attempt.getOperatingSystem(),
                        "failureReason", attempt.getFailureReason(),
                        "attemptedAt", attempt.getAttemptedAt()
                ))
                .toList();
    }

    private LocalDate parseReportDate(String value, LocalDate fallback) {
        return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return value != null && !value.isBefore(startInclusive) && value.isBefore(endExclusive);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 21. CANCELLATION REQUESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/cancellation-requests")
    public ResponseEntity<List<Map<String, Object>>> getCancellationRequests(
            @RequestParam(required = false) String status) {
        List<CancellationRequest> requests = status != null
                ? cancellationRequestRepository.findAllByStatusOrderByCreatedAtDesc(CancellationRequestStatus.valueOf(status.toUpperCase()))
                : cancellationRequestRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(requests.stream().map(this::cancellationRequestRow).collect(Collectors.toList()));
    }

    @PatchMapping("/cancellation-requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveCancellationRequest(@PathVariable Long id) {
        CancellationRequest request = cancellationRequestRepository.findById(id)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException("Cancellation request not found"));
        if (request.getStatus() != CancellationRequestStatus.PENDING) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false, "message", "Only pending requests can be approved."));
        }
        Tenant tenant = request.getTenant();
        tenant.setStatus("CANCELLED");
        tenant.setSubscriptionActive(false);
        tenantRepository.save(tenant);

        request.setStatus(CancellationRequestStatus.APPROVED);
        request.setReviewedBy(currentSuperAdminEmail());
        request.setReviewedAt(LocalDateTime.now());
        cancellationRequestRepository.save(request);

        logAgencyAction(tenant, "SUBSCRIPTION_CANCELLATION_APPROVED", "Cancellation request #" + id + " approved");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cancellation request approved. The subscription has been cancelled.",
                "data", cancellationRequestRow(request)
        ));
    }

    @PatchMapping("/cancellation-requests/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectCancellationRequest(
            @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        CancellationRequest request = cancellationRequestRepository.findById(id)
                .orElseThrow(() -> new com.carrental.exception.ResourceNotFoundException("Cancellation request not found"));
        if (request.getStatus() != CancellationRequestStatus.PENDING) {
            return ResponseEntity.status(409).body(Map.of(
                    "success", false, "message", "Only pending requests can be rejected."));
        }
        request.setStatus(CancellationRequestStatus.REJECTED);
        request.setReviewedBy(currentSuperAdminEmail());
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(body != null ? body.get("note") : null);
        cancellationRequestRepository.save(request);

        logAgencyAction(request.getTenant(), "SUBSCRIPTION_CANCELLATION_REJECTED", "Cancellation request #" + id + " rejected");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cancellation request rejected.",
                "data", cancellationRequestRow(request)
        ));
    }

    private Map<String, Object> cancellationRequestRow(CancellationRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", request.getId());
        map.put("tenantId", request.getTenant().getId());
        map.put("agencyName", request.getTenant().getName());
        map.put("requestedByUserId", request.getRequestedByUserId());
        map.put("reason", request.getReason());
        map.put("feedback", request.getFeedback());
        map.put("status", request.getStatus());
        map.put("reviewedBy", request.getReviewedBy());
        map.put("reviewedAt", request.getReviewedAt());
        map.put("reviewNote", request.getReviewNote());
        map.put("createdAt", request.getCreatedAt());
        return map;
    }

    private List<String> reportColumns(String type, List<Map<String, Object>> rows) {
        if (!rows.isEmpty()) return new ArrayList<>(rows.get(0).keySet());
        return switch (type) {
            case "revenue" -> List.of("paymentNumber", "agency", "amount", "status", "type", "paymentDate");
            case "agencies" -> List.of("id", "name", "email", "status", "verificationStatus", "planName", "subscriptionActive", "createdAt");
            case "gps" -> List.of("agency", "provider", "enabled", "connectionStatus", "activeDevices", "alertsInRange", "lastSyncAt");
            case "support" -> List.of("ticketNumber", "subject", "status", "priority", "createdBy", "assignedTo", "createdAt", "resolvedAt");
            case "security" -> List.of("email", "ipAddress", "success", "suspicious", "browser", "operatingSystem", "failureReason", "attemptedAt");
            default -> List.of();
        };
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(values[i].toString(), values[i + 1]);
        }
        return row;
    }

    private String toCsv(List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(this::csvValue).collect(Collectors.joining(","))).append("\n");
        rows.forEach(row -> csv.append(columns.stream()
                .map(column -> csvValue(row.get(column)))
                .collect(Collectors.joining(","))).append("\n"));
        return csv.toString();
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

}
