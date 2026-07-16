package com.carrental.service;

import com.carrental.entity.Contract;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.AdditionalDriverRepository;
import com.carrental.repository.ContractAuditLogRepository;
import com.carrental.repository.ContractDocumentRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.repository.InspectionRepository;
import com.carrental.repository.NotificationRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.VehicleConditionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Permanently deletes a trashed contract and every row that references it.
 * Used both by the manual "Delete permanently" action and by
 * {@link ContractTrashCleanupJob}'s daily auto-purge.
 *
 * <p><strong>Why native queries everywhere?</strong><br>
 * Spring Data JPA derived-delete methods (e.g. {@code deleteAllByContractId})
 * first execute a JPQL SELECT that navigates {@code e.contract.id}, causing
 * Hibernate to JOIN the {@code contracts} table and apply the entity-level
 * {@code @SQLRestriction("coalesce(deleted,false)=false")} filter. Because the
 * contract is soft-deleted ({@code deleted=true}), the JOIN returns 0 rows, no
 * child records are deleted, and the subsequent native {@code DELETE FROM contracts}
 * hits FK violations — surfaced as a 409 Conflict. Every delete here therefore
 * uses a {@code nativeQuery=true} {@code @Modifying} query that bypasses
 * Hibernate entirely and goes straight to SQL. The InspectionRepository already
 * documented and fixed this pattern; all other child-table repositories now do
 * the same.</p>
 *
 * <p>Clients, vehicles, tenants and users are never touched by this service.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractPurgeService {

    private final ContractRepository        contractRepository;
    private final PaymentRepository         paymentRepository;
    private final DepositRepository         depositRepository;
    private final InspectionRepository      inspectionRepository;
    private final NotificationRepository    notificationRepository;
    private final AdditionalDriverRepository additionalDriverRepository;
    private final ContractAuditLogRepository contractAuditLogRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final VehicleConditionRepository vehicleConditionRepository;

    /** Manual purge — only ever targets a contract already in trash for this tenant. */
    @Transactional
    public Map<String, Object> purgeContract(Long contractId, Long tenantId) {
        Contract contract = contractRepository.findDeletedByIdAndTenantId(contractId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trashed contract not found with id: " + contractId));
        return purge(contract);
    }

    /** Core purge logic — also called per-contract by the daily auto-purge job. */
    @Transactional
    public Map<String, Object> purge(Contract contract) {
        Long   id             = contract.getId();
        String contractNumber = contract.getContractNumber();
        Boolean deleted       = contract.getDeleted();

        // Resolve nullable lazy associations safely (no Hibernate proxy traversal needed).
        Long reservationId = contract.getReservation() != null ? contract.getReservation().getId() : null;
        Long vehicleId     = contract.getVehicle()     != null ? contract.getVehicle().getId()     : null;

        log.debug("[CONTRACT_PURGE_DEBUG] contractId={} contractNumber={} deleted={} reservationId={} vehicleId={} — starting permanent delete",
                id, contractNumber, deleted, reservationId, vehicleId);

        // ── Step 1: inspection_media (FK: inspection_media.inspection_id → inspections.id) ──
        // Must run before inspections; native query already in InspectionRepository.
        inspectionRepository.deleteMediaByContractId(id);
        log.debug("[CONTRACT_PURGE_DEBUG] contractId={} inspectionMediaDeleted=performed", id);

        // ── Step 2: inspections (FK: inspections.contract_id → contracts.id) ──
        inspectionRepository.deleteAllByContractId(id);
        log.debug("[CONTRACT_PURGE_DEBUG] contractId={} inspectionsDeleted=performed", id);

        // ── Step 3: payments (FK: payments.contract_id → contracts.id) ──
        int paymentsDeleted = paymentRepository.deleteNativeByContractId(id);
        log.debug("[CONTRACT_PURGE_DEBUG] contractId={} paymentsDeleted={}", id, paymentsDeleted);

        // ── Step 4: deposits (FK: deposits.contract_id → contracts.id) ──
        int depositsDeleted = depositRepository.deleteNativeByContractId(id);
        log.debug("[CONTRACT_PURGE_DEBUG] contractId={} depositsDeleted={}", id, depositsDeleted);

        // ── Step 5: notifications (contract_id is a plain Long, no FK constraint) ──
        // Derived delete works here because Notification.contractId is NOT a @ManyToOne
        // (no @SQLRestriction join). This is cleanup only — it does not block the DELETE.
        notificationRepository.deleteAllByContractId(id);
        log.debug("[CONTRACT_PURGE_DEBUG] contractId={} notificationsDeleted=performed", id);

        // ── Step 6: contract child tables (all have FK contract_id → contracts.id) ──
        int driversDeleted    = additionalDriverRepository.deleteNativeByContractId(id);
        int conditionsDeleted = vehicleConditionRepository.deleteNativeByContractId(id);
        int docsDeleted       = contractDocumentRepository.deleteNativeByContractId(id);
        int auditLogsDeleted  = contractAuditLogRepository.deleteNativeByContractId(id);
        log.debug("[CONTRACT_PURGE_DEBUG] contractId={} additionalDriversDeleted={} vehicleConditionsDeleted={} documentsDeleted={} auditLogsDeleted={}",
                id, driversDeleted, conditionsDeleted, docsDeleted, auditLogsDeleted);

        // ── Step 7: hard-delete the contract row ──
        // Native DELETE bypasses @SQLRestriction so the soft-deleted (deleted=true) row
        // is found and removed — em.delete() with the filter active would silently no-op.
        contractRepository.deleteNativeById(id);

        log.debug("[CONTRACT_PURGE_DEBUG] contractId={} contractNumber={} deleted={} reservationId={} vehicleId={} " +
                 "paymentsDeleted={} depositsDeleted={} documentsDeleted={} auditLogsDeleted={} " +
                 "inspectionsDeleted=performed inspectionMediaDeleted=performed notificationsDeleted=performed " +
                 "reservationDeleted=false vehicleDeleted=false clientDeleted=false purgeSuccess=true",
                id, contractNumber, deleted, reservationId, vehicleId,
                paymentsDeleted, depositsDeleted, docsDeleted, auditLogsDeleted);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractId",     id);
        result.put("contractNumber", contractNumber);
        result.put("purged",         true);
        return result;
    }
}
