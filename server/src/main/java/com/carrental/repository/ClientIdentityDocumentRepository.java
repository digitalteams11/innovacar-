package com.carrental.repository;

import com.carrental.entity.ClientIdentityDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientIdentityDocumentRepository extends JpaRepository<ClientIdentityDocument, Long> {

    List<ClientIdentityDocument> findAllByClientIdAndTenantId(Long clientId, Long tenantId);

    Optional<ClientIdentityDocument> findFirstByClientIdAndTenantIdAndIsPrimaryTrue(Long clientId, Long tenantId);

    boolean existsByTenantIdAndDocumentNumberIgnoreCaseAndIsPrimaryTrue(Long tenantId, String documentNumber);

    Optional<ClientIdentityDocument> findFirstByTenantIdAndDocumentNumberIgnoreCaseAndIsPrimaryTrue(
            Long tenantId, String documentNumber);

    /**
     * Excludes a given client's own row — used when updating a client's
     * existing primary document so it doesn't collide with itself.
     */
    boolean existsByTenantIdAndDocumentNumberIgnoreCaseAndIsPrimaryTrueAndClientIdNot(
            Long tenantId, String documentNumber, Long clientId);

    Optional<ClientIdentityDocument> findFirstByTenantIdAndDocumentNumberIgnoreCaseAndIsPrimaryTrueAndClientIdNot(
            Long tenantId, String documentNumber, Long clientId);
}
