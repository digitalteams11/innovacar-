package com.carrental.service;

import com.carrental.dto.contract.CreateContractRequest;
import com.carrental.dto.contract.UpdateContractRequest;
import com.carrental.dto.contract.ContractResponse;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Contract-management business logic.
 *
 * <p><strong>Tenant isolation:</strong> every query is scoped to the
 * {@code tenantId} extracted from the JWT via {@link TenantContext}.
 * A user of tenant A will always receive a 404 for contracts that
 * belong to tenant B — preventing both data leakage and enumeration.
 *
 * <p><strong>Access policy (enforced at controller level):</strong>
 * Any authenticated user may read contracts. Only ADMIN users may
 * create, update, or delete them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final TenantRepository   tenantRepository;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all contracts for the caller's tenant.
     */
    @Transactional(readOnly = true)
    public List<ContractResponse> getAllContracts() {
        Long tenantId = TenantContext.getCurrentTenantId();
        log.debug("Listing contracts for tenant [{}]", tenantId);

        return contractRepository.findAllByTenantId(tenantId)
                .stream()
                .map(ContractResponse::from)
                .toList();
    }

    /**
     * Fetches a single contract scoped to the caller's tenant.
     *
     * @throws ResourceNotFoundException if the contract does not exist in this tenant
     */
    @Transactional(readOnly = true)
    public ContractResponse getContractById(Long id) {
        return ContractResponse.from(fetchContractInTenant(id));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Adds a new contract to the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the tenant record cannot be found
     */
    @Transactional
    public ContractResponse createContract(CreateContractRequest request) {
        Long   tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant   = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        ContractStatus status = request.getStatus() != null
                ? request.getStatus()
                : ContractStatus.PENDING;

        Contract contract = contractRepository.save(Contract.builder()
                .contractNumber(request.getContractNumber())
                .clientName(request.getClientName())
                .vehicleMarque(request.getVehicleMarque())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(status)
                .totalAmount(request.getTotalAmount())
                .tenant(tenant)
                .build());

        log.info("Created contract [id={}] '{}' in tenant [{}]",
                contract.getId(), contract.getContractNumber(), tenantId);

        return ContractResponse.from(contract);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields in {@code request} are applied.
     * ADMIN-only.
     *
     * @throws ResourceNotFoundException if the contract is not found in this tenant
     */
    @Transactional
    public ContractResponse updateContract(Long id, UpdateContractRequest request) {
        Contract contract = fetchContractInTenant(id);

        if (StringUtils.hasText(request.getContractNumber())) {
            contract.setContractNumber(request.getContractNumber());
        }
        if (StringUtils.hasText(request.getClientName())) {
            contract.setClientName(request.getClientName());
        }
        if (StringUtils.hasText(request.getVehicleMarque())) {
            contract.setVehicleMarque(request.getVehicleMarque());
        }
        if (request.getStartDate() != null) {
            contract.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            contract.setEndDate(request.getEndDate());
        }
        if (request.getStatus() != null) {
            contract.setStatus(request.getStatus());
        }
        if (request.getTotalAmount() != null) {
            contract.setTotalAmount(request.getTotalAmount());
        }

        Contract saved = contractRepository.save(contract);
        log.info("Updated contract [id={}] in tenant [{}]", id, TenantContext.getCurrentTenantId());
        return ContractResponse.from(saved);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes a contract from the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the contract is not found in this tenant
     */
    @Transactional
    public void deleteContract(Long id) {
        Contract contract = fetchContractInTenant(id);
        contractRepository.delete(contract);
        log.info("Deleted contract [id={}] from tenant [{}]",
                id, TenantContext.getCurrentTenantId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tenant-scoped contract lookup. Returns 404 for both missing and
     * cross-tenant contracts so tenant B cannot discover tenant A's IDs.
     */
    private Contract fetchContractInTenant(Long contractId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return contractRepository.findByIdAndTenantId(contractId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Contract not found with id: " + contractId));
    }
}
