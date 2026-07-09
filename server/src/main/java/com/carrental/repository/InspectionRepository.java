package com.carrental.repository;

import com.carrental.entity.Inspection;
import com.carrental.entity.InspectionStatus;
import com.carrental.entity.InspectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    Optional<Inspection> findByToken(String token);
    Optional<Inspection> findByIdAndTenantId(Long id, Long tenantId);
    List<Inspection> findAllByTenantIdAndReservationIdOrderByCreatedAtDesc(Long tenantId, Long reservationId);
    List<Inspection> findAllByTenantIdAndClientIdOrderByCreatedAtDesc(Long tenantId, Long clientId);
    List<Inspection> findAllByTenantIdAndContractIdOrderByCreatedAtDesc(Long tenantId, Long contractId);
    List<Inspection> findAllByMediaExpiresAtBeforeAndStatusNot(LocalDateTime mediaExpiresAt, InspectionStatus status);
    Optional<Inspection> findFirstByTenantIdAndContractIdAndTypeAndStatusNotOrderByCreatedAtDesc(
            Long tenantId, Long contractId, InspectionType type, InspectionStatus status);
    Optional<Inspection> findFirstByTenantIdAndReservationIdAndTypeAndStatusNotOrderByCreatedAtDesc(
            Long tenantId, Long reservationId, InspectionType type, InspectionStatus status);
    boolean existsByTenantIdAndContractIdAndTypeAndStatus(Long tenantId, Long contractId, InspectionType type, InspectionStatus status);

    /**
     * Deletes inspection_media rows for inspections linked to this contract,
     * then deletes the inspections themselves.
     *
     * Native SQL is required here because Spring Data's derived delete would try
     * to join contracts (applying @SQLRestriction coalesce(deleted,false)=false),
     * which returns 0 rows for soft-deleted contracts — leaving inspection_media
     * rows behind and causing a FK violation when the inspection rows are then
     * removed. Two explicit native DELETEs bypass that entirely.
     */
    @Modifying
    @Query(value = "DELETE FROM inspection_media WHERE inspection_id IN " +
                   "(SELECT id FROM inspections WHERE contract_id = :contractId)",
           nativeQuery = true)
    void deleteMediaByContractId(@Param("contractId") Long contractId);

    @Modifying
    @Query(value = "DELETE FROM inspections WHERE contract_id = :contractId",
           nativeQuery = true)
    void deleteAllByContractId(@Param("contractId") Long contractId);
}
