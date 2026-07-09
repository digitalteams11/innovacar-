package com.carrental.service;

import com.carrental.entity.Contract;
import com.carrental.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily job that permanently purges contracts that have been sitting in
 * trash longer than {@code app.contracts.trash-retention-days}. Each
 * contract is purged independently so one bad row can't block the rest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractTrashCleanupJob {

    private final ContractRepository contractRepository;
    private final ContractPurgeService contractPurgeService;

    @Value("${app.contracts.trash-retention-days:${app.trash.retention-days:30}}")
    private int trashRetentionDays;

    @Scheduled(cron = "${app.contracts.trash-purge.cron:0 30 3 * * *}")
    public void purgeExpiredTrash() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(trashRetentionDays);
        List<Contract> expired = contractRepository.findExpiredTrash(cutoff);
        if (expired.isEmpty()) {
            return;
        }
        log.info("[TRASH_PURGE] starting auto-purge of {} expired trashed contract(s)", expired.size());
        for (Contract contract : expired) {
            Long contractId = contract.getId();
            String contractNumber = contract.getContractNumber();
            LocalDateTime deletedAt = contract.getDeletedAt();
            try {
                contractPurgeService.purge(contract);
                log.info("[TRASH_PURGE] entity=CONTRACT id={} contractNumber={} deletedAt={} purgedAt={}",
                        contractId, contractNumber, deletedAt, LocalDateTime.now());
            } catch (Exception ex) {
                log.error("[TRASH_PURGE] entity=CONTRACT id={} failed to purge: {}",
                        contractId, ex.getMessage(), ex);
            }
        }
    }
}
