package com.carrental.repository;

import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    /** Tenant-scoped lookup by invoice number. */
    Optional<Invoice> findByInvoiceNumberAndTenantId(String invoiceNumber, Long tenantId);
}
