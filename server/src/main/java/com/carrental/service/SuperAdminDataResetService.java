package com.carrental.service;

import com.carrental.dto.superadmin.datareset.DataResetAction;
import com.carrental.dto.superadmin.datareset.DataResetRequest;
import com.carrental.dto.superadmin.datareset.DataResetScope;
import com.carrental.entity.*;
import com.carrental.exception.DataResetSecurityException;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Backs the Super Admin "Data Reset Center". Every destructive action here
 * must pass through {@link #execute} which enforces, in order: super-owner
 * permission, email verification, 2FA enabled + valid code, password
 * confirmation, and an exact confirmation-code match — before a single row
 * is touched. All deletes are scoped by tenant_id (never a bare DELETE), and
 * every attempt is written to {@link DataResetAuditLog} before and after.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminDataResetService {

    private final TenantRepository tenantRepository;
    private final ClientRepository clientRepository;
    private final ContractRepository contractRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final DepositRepository depositRepository;
    private final GpsSettingsRepository gpsSettingsRepository;
    private final GpsAlertRepository gpsAlertRepository;
    private final AiAuditLogRepository aiAuditLogRepository;
    private final AdditionalDriverRepository additionalDriverRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final VehicleConditionRepository vehicleConditionRepository;
    private final ContractAuditLogRepository contractAuditLogRepository;
    private final DataResetAuditRepository dataResetAuditRepository;
    private final BackupRecordRepository backupRecordRepository;
    private final BackupService backupService;
    private final TwoFactorService twoFactorService;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    private static final Set<ContractStatus> NON_FINAL_CONTRACT_STATUSES = Set.of(
            ContractStatus.DRAFT, ContractStatus.WAITING_SIGNATURE, ContractStatus.WAITING_CLIENT_SIGNATURE,
            ContractStatus.PENDING_SIGNATURE, ContractStatus.PARTIALLY_SIGNED, ContractStatus.SIGNED,
            ContractStatus.ACTIVE, ContractStatus.PAID);

    // ── Status ───────────────────────────────────────────────────────────────

    public Map<String, Object> status(User currentUser) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emailVerified", Boolean.TRUE.equals(currentUser.getEmailVerified()));
        data.put("twoFactorEnabled", Boolean.TRUE.equals(currentUser.getTwoFactorEnabled()));
        data.put("environment", isDevProfile() ? "development" : "production");
        data.put("backupAvailable", backupRecordRepository.findAllByOrderByCreatedAtDesc().stream()
                .anyMatch(b -> b.getStatus() == BackupRecord.Status.COMPLETED));
        dataResetAuditRepository.findFirstByOrderByCreatedAtDesc().ifPresentOrElse(
                last -> data.put("lastResetAction", summarize(last)),
                () -> data.put("lastResetAction", null));
        data.put("isSuperOwner", isSuperOwner(currentUser));
        data.put("dangerousActionsEnabled", isSuperOwner(currentUser));
        return data;
    }

    // ── Preview ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> preview(DataResetRequest request, User currentUser) {
        requirePermission(currentUser);

        if (request.getAction() == DataResetAction.FULL_PLATFORM_RESET) {
            requireScope(request, DataResetScope.PLATFORM);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("agencyName", "All agencies (platform-wide)");
            data.put("willDelete", platformWideCounts());
            data.put("willKeep", Map.of("agencyAccounts", true, "users", true, "subscriptions", true));
            data.put("confirmationCode", confirmationCodeForPlatform());
            data.put("productionBlocked", !isDevProfile());
            return data;
        }

        if (request.getAction() == DataResetAction.DELETE_CLIENT) {
            requireScope(request, DataResetScope.CLIENT);
            Tenant tenant = resolveTenant(request.getAgencyId());
            Client client = clientRepository.findByIdAndTenantId(request.getClientId(), tenant.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found in this agency"));
            List<Contract> nonFinalContracts = contractRepository
                    .findAllByTenantIdAndClientId(tenant.getId(), client.getId()).stream()
                    .filter(c -> NON_FINAL_CONTRACT_STATUSES.contains(c.getStatus()))
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("agencyName", tenant.getName());
            data.put("clientName", client.getName());
            data.put("willDelete", Map.of("clients", 1));
            data.put("blockingActiveContracts", nonFinalContracts.size());
            data.put("willKeep", Map.of("agencyAccount", true, "otherClients", true));
            data.put("confirmationCode", confirmationCodeFor(tenant));
            return data;
        }

        if (request.getAction() == DataResetAction.RESET_AGENCY_TEST_DATA) {
            throw new DataResetSecurityException(HttpStatus.BAD_REQUEST, "ACTION_NOT_SUPPORTED",
                    "This schema has no test-data marker on clients/vehicles/contracts, so test-data-only reset cannot be performed safely. Use RESET_OPERATIONAL_DATA on a dedicated test agency instead.");
        }

        // AGENCY-scoped actions: RESET_OPERATIONAL_DATA, RESET_GPS_DATA, RESET_AI_DATA, FULL_AGENCY_RESET
        requireScope(request, DataResetScope.AGENCY);
        Tenant tenant = resolveTenant(request.getAgencyId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agencyName", tenant.getName());
        data.put("willDelete", willDeleteCountsFor(request.getAction(), tenant.getId()));
        data.put("willKeep", willKeepFor(request.getAction()));
        data.put("confirmationCode", confirmationCodeFor(tenant));
        return data;
    }

    private Map<String, Object> willDeleteCountsFor(DataResetAction action, Long tenantId) {
        Map<String, Object> counts = new LinkedHashMap<>();
        if (action == DataResetAction.RESET_OPERATIONAL_DATA || action == DataResetAction.FULL_AGENCY_RESET) {
            counts.put("clients", clientRepository.countByTenantIdAndDeletedFalse(tenantId));
            counts.put("contracts", contractRepository.countByTenantId(tenantId));
            counts.put("reservations", reservationRepository.countByTenantId(tenantId));
            counts.put("payments", paymentRepository.countByTenantId(tenantId));
            counts.put("invoices", invoiceRepository.countByTenantId(tenantId));
            counts.put("deposits", depositRepository.countByTenantId(tenantId));
        }
        if (action == DataResetAction.RESET_GPS_DATA || action == DataResetAction.FULL_AGENCY_RESET) {
            counts.put("gpsSettings", gpsSettingsRepository.existsByTenantId(tenantId) ? 1 : 0);
            counts.put("gpsAlerts", gpsAlertRepository.countByTenantId(tenantId));
        }
        if (action == DataResetAction.RESET_AI_DATA || action == DataResetAction.FULL_AGENCY_RESET) {
            counts.put("aiAuditLogs", aiAuditLogRepository.countByAgencyId(tenantId));
        }
        return counts;
    }

    private Map<String, Object> willKeepFor(DataResetAction action) {
        Map<String, Object> kept = new LinkedHashMap<>();
        kept.put("agencyAccount", true);
        kept.put("users", true);
        kept.put("subscription", true);
        kept.put("settings", true);
        if (action == DataResetAction.RESET_GPS_DATA || action == DataResetAction.RESET_AI_DATA) {
            kept.put("clients", true);
            kept.put("contracts", true);
            kept.put("reservations", true);
        }
        return kept;
    }

    private Map<String, Object> platformWideCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("agencies", tenantRepository.count());
        counts.put("clients", clientRepository.count());
        counts.put("contracts", contractRepository.count());
        counts.put("reservations", reservationRepository.count());
        counts.put("payments", paymentRepository.count());
        counts.put("invoices", invoiceRepository.count());
        return counts;
    }

    // ── Execute ──────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> execute(DataResetRequest request, User currentUser, String ipAddress) {
        log.debug("[DATA_RESET_CONFIRM_DEBUG] superAdminId={} scope={} agencyId={} action={} previewLoaded=true",
                currentUser.getId(), request.getScope(), request.getAgencyId(), request.getAction());
        requirePermission(currentUser);
        requireEmailVerified(currentUser);
        requireTwoFactorEnabled(currentUser);
        boolean passwordValid = false;
        try {
            requirePasswordValid(currentUser, request.getCurrentPassword());
            passwordValid = true;
        } catch (DataResetSecurityException ex) {
            log.debug("[DATA_RESET_CONFIRM_DEBUG] superAdminId={} passwordValid=false errorCode={}",
                    currentUser.getId(), ex.getErrorCode());
            throw ex;
        }
        boolean twoFactorValid = false;
        try {
            requireTwoFactorCodeValid(currentUser, request.getTwoFactorCode());
            twoFactorValid = true;
        } catch (DataResetSecurityException ex) {
            log.debug("[DATA_RESET_CONFIRM_DEBUG] superAdminId={} passwordValid={} twoFactorValid=false errorCode={}",
                    currentUser.getId(), passwordValid, ex.getErrorCode());
            throw ex;
        }
        log.debug("[DATA_RESET_CONFIRM_DEBUG] superAdminId={} passwordValid={} twoFactorValid={} — validating confirmationCode",
                currentUser.getId(), passwordValid, twoFactorValid);

        if (request.getAction() == DataResetAction.FULL_PLATFORM_RESET && !isDevProfile()) {
            throw new DataResetSecurityException(HttpStatus.FORBIDDEN, "PLATFORM_RESET_DISABLED_IN_PRODUCTION",
                    "Full platform reset is disabled in production.");
        }
        if (request.getAction() == DataResetAction.RESET_AGENCY_TEST_DATA) {
            throw new DataResetSecurityException(HttpStatus.BAD_REQUEST, "ACTION_NOT_SUPPORTED",
                    "This schema has no test-data marker, so test-data-only reset cannot be performed safely.");
        }

        Tenant tenant = request.getScope() == DataResetScope.PLATFORM ? null : resolveTenant(request.getAgencyId());
        requireConfirmationCode(request, tenant);
        log.debug("[DATA_RESET_CONFIRM_DEBUG] superAdminId={} agencyId={} agencyName={} action={} executeAllowed=true",
                currentUser.getId(), tenant != null ? tenant.getId() : null,
                tenant != null ? tenant.getName() : "PLATFORM", request.getAction());

        DataResetAuditLog audit = DataResetAuditLog.builder()
                .scope(request.getScope().name())
                .action(request.getAction().name())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantName(tenant != null ? tenant.getName() : "PLATFORM")
                .clientId(request.getClientId())
                .status(DataResetAuditLog.Status.STARTED)
                .performedById(currentUser.getId())
                .performedByEmail(currentUser.getEmail())
                .ipAddress(ipAddress)
                .requestSummary(request.getScope() + ":" + request.getAction()
                        + (tenant != null ? " agency=" + tenant.getId() : "")
                        + (request.getClientId() != null ? " client=" + request.getClientId() : ""))
                .build();
        audit = dataResetAuditRepository.save(audit);

        try {
            createPreResetBackup();
            Map<String, Object> result = switch (request.getAction()) {
                case DELETE_CLIENT -> deleteClient(tenant, request.getClientId(), request.isForce());
                case RESET_OPERATIONAL_DATA -> resetOperationalData(tenant.getId());
                case RESET_GPS_DATA -> resetGpsData(tenant.getId());
                case RESET_AI_DATA -> resetAiData(tenant.getId());
                case FULL_AGENCY_RESET -> fullAgencyReset(tenant.getId());
                case FULL_PLATFORM_RESET -> fullPlatformReset();
                case RESET_AGENCY_TEST_DATA -> throw new IllegalStateException("unreachable");
            };
            audit.setStatus(DataResetAuditLog.Status.SUCCESS);
            audit.setResultSummary(String.valueOf(result));
            audit.setCompletedAt(LocalDateTime.now());
            dataResetAuditRepository.save(audit);
            return result;
        } catch (DataResetSecurityException ex) {
            audit.setStatus(DataResetAuditLog.Status.FAILED);
            audit.setErrorMessage(ex.getMessage());
            audit.setCompletedAt(LocalDateTime.now());
            dataResetAuditRepository.save(audit);
            throw ex;
        } catch (RuntimeException ex) {
            audit.setStatus(DataResetAuditLog.Status.FAILED);
            audit.setErrorMessage(ex.getMessage());
            audit.setCompletedAt(LocalDateTime.now());
            dataResetAuditRepository.save(audit);
            throw ex;
        }
    }

    private void createPreResetBackup() {
        try {
            backupService.createManual();
        } catch (Exception ex) {
            log.warn("Pre-reset backup could not be created — proceeding without it: {}", ex.getMessage());
        }
    }

    private Map<String, Object> deleteClient(Tenant tenant, Long clientId, boolean force) {
        Client client = clientRepository.findByIdAndTenantId(clientId, tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found in this agency"));
        boolean hasNonFinalContract = contractRepository
                .findAllByTenantIdAndClientId(tenant.getId(), clientId).stream()
                .anyMatch(c -> NON_FINAL_CONTRACT_STATUSES.contains(c.getStatus()));
        if (hasNonFinalContract && !force) {
            throw new DataResetSecurityException(HttpStatus.CONFLICT, "CLIENT_HAS_ACTIVE_CONTRACT",
                    "This client has an active or draft contract. Pass force=true to delete anyway.");
        }
        client.setDeleted(true);
        client.setDeletedAt(LocalDateTime.now());
        client.setDeletedBy("super-admin-data-reset");
        clientRepository.save(client);
        return Map.of("deletedClients", 1);
    }

    /** Payments/deposits before invoices/contracts before reservations — strict FK dependency order. */
    private Map<String, Object> resetOperationalData(Long tenantId) {
        long deletedClients = clientRepository.countByTenantIdAndDeletedFalse(tenantId);
        long deletedContracts = contractRepository.countByTenantId(tenantId);
        long deletedReservations = reservationRepository.countByTenantId(tenantId);
        long deletedPayments = paymentRepository.countByTenantId(tenantId);
        long deletedInvoices = invoiceRepository.countByTenantId(tenantId);
        long deletedDeposits = depositRepository.countByTenantId(tenantId);

        List<Long> contractIds = contractRepository.findAllByTenantId(tenantId).stream()
                .map(Contract::getId).toList();

        paymentRepository.deleteAllByTenantId(tenantId);
        depositRepository.deleteAllByTenantId(tenantId);
        invoiceRepository.deleteAllByTenantId(tenantId);

        if (!contractIds.isEmpty()) {
            contractAuditLogRepository.deleteAllByContractIdIn(contractIds);
            additionalDriverRepository.deleteAllByContractIdIn(contractIds);
            contractDocumentRepository.deleteAllByContractIdIn(contractIds);
            vehicleConditionRepository.deleteAllByContractIdIn(contractIds);
        }
        contractRepository.deleteAllByTenantId(tenantId);
        reservationRepository.deleteAllByTenantId(tenantId);

        // Clients are soft-deleted, never hard-deleted, by a reset.
        clientRepository.findAllByTenantIdAndDeletedFalse(tenantId).forEach(client -> {
            client.setDeleted(true);
            client.setDeletedAt(LocalDateTime.now());
            client.setDeletedBy("super-admin-data-reset");
            clientRepository.save(client);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedClients", deletedClients);
        result.put("deletedContracts", deletedContracts);
        result.put("deletedReservations", deletedReservations);
        result.put("deletedPayments", deletedPayments);
        result.put("deletedInvoices", deletedInvoices);
        result.put("deletedDeposits", deletedDeposits);
        return result;
    }

    private Map<String, Object> resetGpsData(Long tenantId) {
        long deletedAlerts = gpsAlertRepository.countByTenantId(tenantId);
        boolean hadSettings = gpsSettingsRepository.existsByTenantId(tenantId);
        gpsAlertRepository.deleteAllByTenantId(tenantId);
        gpsSettingsRepository.deleteByTenantId(tenantId);
        return Map.of("deletedGpsSettings", hadSettings ? 1 : 0, "deletedGpsAlerts", deletedAlerts);
    }

    private Map<String, Object> resetAiData(Long tenantId) {
        long deletedLogs = aiAuditLogRepository.countByAgencyId(tenantId);
        aiAuditLogRepository.deleteAllByAgencyId(tenantId);
        return Map.of("deletedAiAuditLogs", deletedLogs);
    }

    private Map<String, Object> fullAgencyReset(Long tenantId) {
        Map<String, Object> result = new LinkedHashMap<>(resetOperationalData(tenantId));
        result.putAll(resetGpsData(tenantId));
        result.putAll(resetAiData(tenantId));
        return result;
    }

    /** Always loops per-tenant so every delete still carries a WHERE tenant_id — never a bare DELETE. */
    private Map<String, Object> fullPlatformReset() {
        long totalAgencies = 0;
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Tenant tenant : tenantRepository.findAll()) {
            Map<String, Object> agencyResult = fullAgencyReset(tenant.getId());
            totalAgencies++;
            agencyResult.forEach((key, value) ->
                    totals.merge(key, ((Number) value).longValue(), Long::sum));
        }
        Map<String, Object> result = new LinkedHashMap<>(totals);
        result.put("resetAgencies", totalAgencies);
        return result;
    }

    // ── Audit log listing ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditLogs(int limit) {
        return dataResetAuditRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
                .stream().map(this::summarize).toList();
    }

    private Map<String, Object> summarize(DataResetAuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", log.getId());
        row.put("scope", log.getScope());
        row.put("action", log.getAction());
        row.put("tenantName", log.getTenantName());
        row.put("status", log.getStatus());
        row.put("performedByEmail", log.getPerformedByEmail());
        row.put("createdAt", log.getCreatedAt());
        row.put("completedAt", log.getCompletedAt());
        row.put("errorMessage", log.getErrorMessage());
        return row;
    }

    // ── Security gates ──────────────────────────────────────────────────────

    private void requirePermission(User user) {
        if (!isSuperOwner(user)) {
            throw new DataResetSecurityException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                    "You do not have permission to manage role access.");
        }
    }

    private boolean isSuperOwner(User user) {
        if (user.getRole() != Role.SUPER_ADMIN) {
            log.debug("[DATA_RESET_ACCESS_DEBUG] userId={} email={} role={} — not SUPER_ADMIN, denied",
                    user.getId(), user.getEmail(), user.getRole());
            return false;
        }
        SuperAdminRole subRole = user.getSuperAdminRole();
        boolean result = subRole == null || "SUPER_OWNER".equalsIgnoreCase(subRole.getCode());
        log.debug("[DATA_RESET_ACCESS_DEBUG] userId={} email={} role={} superAdminRoleCode={} — isSuperOwner={}",
                user.getId(), user.getEmail(), user.getRole(),
                subRole != null ? subRole.getCode() : "null", result);
        return result;
    }

    private void requireEmailVerified(User user) {
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new DataResetSecurityException(HttpStatus.FORBIDDEN, "EMAIL_VERIFICATION_REQUIRED",
                    "Verify your email before using data reset.");
        }
    }

    private void requireTwoFactorEnabled(User user) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new DataResetSecurityException(HttpStatus.FORBIDDEN, "TWO_FACTOR_REQUIRED",
                    "Enable two-factor authentication before using data reset.");
        }
    }

    private void requirePasswordValid(User user, String currentPassword) {
        if (currentPassword == null || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new DataResetSecurityException(HttpStatus.FORBIDDEN, "PASSWORD_CONFIRMATION_FAILED",
                    "Password confirmation failed.");
        }
    }

    private void requireTwoFactorCodeValid(User user, String code) {
        if (code == null || !twoFactorService.verify(user, code)) {
            throw new DataResetSecurityException(HttpStatus.FORBIDDEN, "TWO_FACTOR_CODE_INVALID",
                    "Invalid two-factor authentication code.");
        }
    }

    private void requireConfirmationCode(DataResetRequest request, Tenant tenant) {
        String expected = tenant != null ? confirmationCodeFor(tenant) : confirmationCodeForPlatform();
        String provided = request.getConfirmationCode();
        boolean matches = provided != null && provided.equals(expected);
        log.debug("[DATA_RESET_CONFIRM_DEBUG] agencyId={} agencyName={} action={} expectedConfirmationCode={} providedConfirmationCodeMatches={}",
                tenant != null ? tenant.getId() : null,
                tenant != null ? tenant.getName() : "PLATFORM",
                request.getAction(), expected, matches);
        if (!matches) {
            throw new DataResetSecurityException(HttpStatus.BAD_REQUEST, "CONFIRMATION_CODE_MISMATCH",
                    "Confirmation code does not match the selected agency. Type " + expected + " exactly.",
                    Map.of("expectedConfirmationCode", expected));
        }
    }

    private void requireScope(DataResetRequest request, DataResetScope expected) {
        if (request.getScope() != expected) {
            throw new DataResetSecurityException(HttpStatus.BAD_REQUEST, "INVALID_SCOPE",
                    "Action " + request.getAction() + " requires scope " + expected + ".");
        }
    }

    private Tenant resolveTenant(Long agencyId) {
        if (agencyId == null) {
            throw new DataResetSecurityException(HttpStatus.BAD_REQUEST, "AGENCY_REQUIRED",
                    "An agencyId is required for this action.");
        }
        return tenantRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
    }

    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("local")
                    || profile.equalsIgnoreCase("development")) {
                return true;
            }
        }
        return false;
    }

    private String confirmationCodeFor(Tenant tenant) {
        String slug = tenant.getName().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return "RESET-" + slug;
    }

    private String confirmationCodeForPlatform() {
        return "RESET-PLATFORM";
    }
}
