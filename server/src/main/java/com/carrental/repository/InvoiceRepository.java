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

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Invoice> findByIdAndTenantId(Long id, Long tenantId);

    /** All invoices of a specific status within a tenant — used for filtering. */
    List<Invoice> findAllByTenantIdAndStatus(Long tenantId, InvoiceStatus status);
}
