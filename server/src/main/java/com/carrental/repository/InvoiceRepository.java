package com.carrental.repository;

import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** All invoices belonging to a tenant. */
    List<Invoice> findAllByTenantId(Long tenantId);

    /** Count of all invoices for a tenant — used by the Super Admin data-reset preview. */
    long countByTenantId(Long tenantId);

    /** Deletes every invoice for a tenant — used by the Super Admin data-reset execute. */
    void deleteAllByTenantId(Long tenantId);

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Invoice> findByIdAndTenantId(Long id, Long tenantId);

    /** All invoices of a specific status within a tenant — used for filtering. */
    List<Invoice> findAllByTenantIdAndStatus(Long tenantId, InvoiceStatus status);

    /** All invoices for a specific client within a tenant. */
    List<Invoice> findAllByTenantIdAndClientId(Long tenantId, Long clientId);

    /** All invoices linked to a specific contract within a tenant — used to keep
     *  invoice status in sync when the contract is cancelled (see ContractService#cancelContract). */
    List<Invoice> findAllByTenantIdAndContractId(Long tenantId, Long contractId);

    /** Tenant-scoped lookup by invoice number. */
    Optional<Invoice> findByInvoiceNumberAndTenantId(String invoiceNumber, Long tenantId);

    /**
     * Dynamic-filter invoice export query, shared verbatim by both the PDF
     * and CSV export endpoints (see InvoiceService#exportFilteredInvoices) so
     * the two formats can never disagree on which invoices matched. Uses the
     * standard "(:param IS NULL OR field = :param)" optional-parameter JPQL
     * pattern — no {@code Specification}/{@code JpaSpecificationExecutor}
     * pattern exists elsewhere in this codebase for list filtering, so this
     * matches PaymentRepository's existing @Query style instead of
     * introducing a new mechanism.
     *
     * <p>{@code tenantId} is always supplied by the service from
     * {@code TenantContext.getCurrentTenantId()} — never from client input —
     * so cross-tenant invoices can never appear in the result regardless of
     * what the filter DTO contains.
     *
     * <p>{@code vehicleId} filters via {@code i.contract.vehicle.id}: an
     * invoice with no linked contract simply never matches a vehicle filter,
     * which is correct (not a bug) per the feature spec.
     */
    @Query("SELECT i FROM Invoice i WHERE i.tenant.id = :tenantId "
            + "AND (:search IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(i.clientName) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "AND (:status IS NULL OR i.status = :status) "
            + "AND (:dateFrom IS NULL OR i.issueDate >= :dateFrom) "
            + "AND (:dateTo IS NULL OR i.issueDate <= :dateTo) "
            + "AND (:clientId IS NULL OR i.client.id = :clientId) "
            + "AND (:contractId IS NULL OR i.contract.id = :contractId) "
            + "AND (:vehicleId IS NULL OR i.contract.vehicle.id = :vehicleId)")
    List<Invoice> findAllForExport(@Param("tenantId") Long tenantId,
                                    @Param("search") String search,
                                    @Param("status") InvoiceStatus status,
                                    @Param("dateFrom") LocalDate dateFrom,
                                    @Param("dateTo") LocalDate dateTo,
                                    @Param("clientId") Long clientId,
                                    @Param("contractId") Long contractId,
                                    @Param("vehicleId") Long vehicleId,
                                    Sort sort);
}
