package com.carrental.service;

import com.carrental.dto.deposit.*;
import com.carrental.entity.*;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositService {

    private final DepositRepository depositRepository;
    private final ContractRepository contractRepository;
    private final ReservationRepository reservationRepository;
    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DepositResponse> getAllDeposits() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return depositRepository.findAllByTenantId(tenantId).stream()
                .map(DepositResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DepositResponse getDepositById(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Deposit deposit = depositRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found"));
        return DepositResponse.from(deposit);
    }

    @Transactional(readOnly = true)
    public DepositResponse getDepositByContractId(Long contractId) {
        Deposit deposit = depositRepository.findByContractId(contractId)
                .orElse(null);
        return DepositResponse.from(deposit);
    }

    @Transactional
    public DepositResponse createDeposit(CreateDepositRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Deposit.DepositType type = parseDepositType(request.getDepositType());

        Deposit deposit = Deposit.builder()
                .depositType(type)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "MAD")
                .reference(request.getReference())
                .notes(request.getNotes())
                .conditionsText(request.getConditionsText() != null ? request.getConditionsText() : defaultConditions())
                .status(DepositStatus.PENDING)
                .tenant(tenant)
                .build();

        // Link to reservation if provided
        if (request.getReservationId() != null) {
            Reservation reservation = reservationRepository.findById(request.getReservationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
            deposit.setReservation(reservation);
            deposit.setClient(reservation.getClient());
        }

        // Link to contract if provided
        if (request.getContractId() != null) {
            Contract contract = contractRepository.findById(request.getContractId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
            deposit.setContract(contract);
            deposit.setClient(contract.getClient());
        }

        // Link to client if explicitly provided
        if (request.getClientId() != null) {
            Client client = clientRepository.findById(request.getClientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
            deposit.setClient(client);
        }

        Deposit saved = depositRepository.save(deposit);
        log.info("Deposit created [id={}, amount={}, type={}] for tenant {}", saved.getId(), saved.getAmount(), type, tenantId);
        return DepositResponse.from(saved);
    }

    @Transactional
    public DepositResponse updateDeposit(Long id, UpdateDepositRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Deposit deposit = depositRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found"));

        if (request.getDepositType() != null) {
            deposit.setDepositType(parseDepositType(request.getDepositType()));
        }
        if (request.getAmount() != null) {
            deposit.setAmount(request.getAmount());
        }
        if (request.getReference() != null) {
            deposit.setReference(request.getReference());
        }
        if (request.getNotes() != null) {
            deposit.setNotes(request.getNotes());
        }
        if (request.getConditionsText() != null) {
            deposit.setConditionsText(request.getConditionsText());
        }
        if (request.getStatus() != null) {
            DepositStatus newStatus = DepositStatus.valueOf(request.getStatus());
            transitionStatus(deposit, newStatus);
        }

        Deposit saved = depositRepository.save(deposit);
        return DepositResponse.from(saved);
    }

    @Transactional
    public DepositResponse markReceived(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Deposit deposit = depositRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found"));
        transitionStatus(deposit, DepositStatus.RECEIVED);
        deposit.setReceivedAt(LocalDateTime.now());
        Deposit saved = depositRepository.save(deposit);
        log.info("Deposit [id={}] marked as RECEIVED", saved.getId());
        return DepositResponse.from(saved);
    }

    @Transactional
    public DepositResponse markHeld(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Deposit deposit = depositRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found"));
        transitionStatus(deposit, DepositStatus.HELD);
        deposit.setHeldAt(LocalDateTime.now());
        Deposit saved = depositRepository.save(deposit);
        log.info("Deposit [id={}] marked as HELD", saved.getId());
        return DepositResponse.from(saved);
    }

    @Transactional
    public DepositResponse processReturn(Long id, DepositReturnRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Deposit deposit = depositRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found"));

        // Validate deductions do not exceed deposit amount
        BigDecimal totalDeductions = request.getDamageDeduction()
                .add(request.getCleaningDeduction())
                .add(request.getLateFeeDeduction())
                .add(request.getFuelDeduction())
                .add(request.getOtherDeduction());

        if (totalDeductions.compareTo(deposit.getAmount()) > 0) {
            throw new IllegalStateException("Total deductions (" + totalDeductions + ") cannot exceed deposit amount (" + deposit.getAmount() + ")");
        }

        // Update deductions
        deposit.setDamageDeduction(request.getDamageDeduction());
        deposit.setCleaningDeduction(request.getCleaningDeduction());
        deposit.setLateFeeDeduction(request.getLateFeeDeduction());
        deposit.setFuelDeduction(request.getFuelDeduction());
        deposit.setOtherDeduction(request.getOtherDeduction());

        // Update return inspection fields
        deposit.setFuelLevelEnd(request.getFuelLevelEnd());
        deposit.setMileageEnd(request.getMileageEnd());
        deposit.setInteriorCondition(request.getInteriorCondition());
        deposit.setExteriorCondition(request.getExteriorCondition());
        deposit.setMissingItems(request.getMissingItems());

        // Calculate returned amount
        BigDecimal returnedAmount = deposit.getAmount().subtract(totalDeductions);
        deposit.setReturnedAmount(returnedAmount);
        deposit.setReturnNotes(request.getReturnNotes());
        deposit.setReturnedAt(LocalDateTime.now());

        // Determine status
        if (totalDeductions.compareTo(BigDecimal.ZERO) == 0) {
            deposit.setStatus(DepositStatus.RETURNED);
        } else if (returnedAmount.compareTo(BigDecimal.ZERO) > 0) {
            deposit.setStatus(DepositStatus.PARTIALLY_RETURNED);
        } else {
            deposit.setStatus(DepositStatus.DEDUCTED);
        }

        Deposit saved = depositRepository.save(deposit);

        // Notify client
        if (saved.getClient() != null && saved.getClient().getEmail() != null) {
            notificationService.createNotification(
                    "Deposit Returned",
                    "Your deposit of " + saved.getAmount() + " " + saved.getCurrency() +
                            " has been processed. Returned: " + returnedAmount +
                            " | Deductions: " + totalDeductions,
                    Notification.NotificationType.CONTRACT_FULLY_SIGNED,
                    saved.getContract() != null ? saved.getContract().getId() : null,
                    tenantId);
        }

        log.info("Deposit [id={}] returned. Deductions={}, Returned={}", saved.getId(), totalDeductions, returnedAmount);
        return DepositResponse.from(saved);
    }

    @Transactional
    public DepositResponse markConditionsAccepted(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Deposit deposit = depositRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found"));
        deposit.setConditionsAccepted(true);
        deposit.setConditionsAcceptedAt(LocalDateTime.now());
        Deposit saved = depositRepository.save(deposit);
        log.info("Deposit [id={}] conditions accepted by client", saved.getId());
        return DepositResponse.from(saved);
    }

    @Transactional
    public void deleteDeposit(Long id) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Deposit deposit = depositRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found"));
        depositRepository.delete(deposit);
        log.info("Deposit [id={}] deleted", id);
    }

    // ── Auto-create from Reservation ─────────────────────────────────────────

    @Transactional
    public Deposit createDepositFromReservation(Reservation reservation, String depositType, String reference, String notes) {
        if (reservation == null || reservation.getDepositAmount() == null || reservation.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Deposit deposit = Deposit.builder()
                .reservation(reservation)
                .client(reservation.getClient())
                .depositType(parseDepositType(depositType))
                .amount(reservation.getDepositAmount())
                .currency("MAD")
                .reference(reference)
                .notes(notes)
                .status(DepositStatus.PENDING)
                .tenant(reservation.getTenant())
                .build();

        return depositRepository.save(deposit);
    }

    // ── Auto-link to Contract ────────────────────────────────────────────────

    @Transactional
    public Deposit linkDepositToContract(Long reservationId, Contract contract) {
        Deposit deposit = depositRepository.findByReservationId(reservationId).orElse(null);
        if (deposit != null) {
            deposit.setContract(contract);
            deposit.setStatus(DepositStatus.HELD);
            deposit.setHeldAt(LocalDateTime.now());
            return depositRepository.save(deposit);
        }
        return null;
    }

    // ── Client Summary ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getClientDepositSummary(Long clientId) {
        Long tenantId = TenantContext.getCurrentTenantId();

        BigDecimal totalDeposits = depositRepository.sumTotalDepositsByClientId(clientId, tenantId);
        BigDecimal activeDeposits = depositRepository.sumActiveDepositsByClientId(clientId, tenantId);
        BigDecimal returnedDeposits = depositRepository.sumReturnedDepositsByClientId(clientId, tenantId);
        Long pendingCount = depositRepository.countPendingDepositsByClientId(clientId, tenantId);

        List<DepositResponse> history = depositRepository.findAllByClientIdAndTenantId(clientId, tenantId).stream()
                .map(DepositResponse::from)
                .collect(Collectors.toList());

        return Map.of(
                "totalDeposits", totalDeposits != null ? totalDeposits : BigDecimal.ZERO,
                "activeDeposits", activeDeposits != null ? activeDeposits : BigDecimal.ZERO,
                "returnedDeposits", returnedDeposits != null ? returnedDeposits : BigDecimal.ZERO,
                "pendingCount", pendingCount != null ? pendingCount : 0L,
                "history", history
        );
    }

    // ── Dashboard Stats ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getDepositStats() {
        Long tenantId = TenantContext.getCurrentTenantId();

        BigDecimal totalHeld = depositRepository.sumActiveDepositsByTenantId(tenantId);
        Long pendingReturns = depositRepository.countPendingReturnsByTenantId(tenantId);
        BigDecimal totalReturned = depositRepository.sumReturnedDepositsByTenantId(tenantId);
        BigDecimal totalDeductions = depositRepository.sumTotalDeductionsByTenantId(tenantId);
        BigDecimal depositRevenue = depositRepository.sumDepositRevenueByTenantId(tenantId);

        return Map.of(
                "totalDepositsHeld", totalHeld != null ? totalHeld : BigDecimal.ZERO,
                "pendingReturns", pendingReturns != null ? pendingReturns : 0L,
                "returnedDeposits", totalReturned != null ? totalReturned : BigDecimal.ZERO,
                "depositDeductions", totalDeductions != null ? totalDeductions : BigDecimal.ZERO,
                "depositRevenue", depositRevenue != null ? depositRevenue : BigDecimal.ZERO
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Deposit.DepositType parseDepositType(String type) {
        if (type == null || type.isBlank()) {
            return Deposit.DepositType.CASH;
        }
        try {
            return Deposit.DepositType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Deposit.DepositType.CASH;
        }
    }

    private void transitionStatus(Deposit deposit, DepositStatus newStatus) {
        // Add validation logic here if needed (e.g. can't go from RETURNED to PENDING)
        deposit.setStatus(newStatus);
    }

    private String defaultConditions() {
        return "The deposit will be returned after inspection of the vehicle and validation of all contractual obligations.";
    }
}
