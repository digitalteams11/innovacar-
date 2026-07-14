package com.carrental.legal.repository;

import com.carrental.legal.entity.LegalAcceptance;
import com.carrental.legal.entity.LegalDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, Long> {

    List<LegalAcceptance> findAllByUserIdOrderByAcceptedAtDesc(Long userId);

    Optional<LegalAcceptance> findFirstByUserIdAndDocumentTypeOrderByVersionNumberDesc(
            Long userId, LegalDocumentType documentType);

    boolean existsByUserIdAndDocumentVersionId(Long userId, Long documentVersionId);

    long countByDocumentVersionId(Long documentVersionId);

    long countByTenantIdAndDocumentType(Long tenantId, LegalDocumentType documentType);
}
