package com.carrental.repository;

import com.carrental.entity.LegalAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, Long> {
    List<LegalAcceptance> findByTenantIdAndUserId(Long tenantId, Long userId);
    Optional<LegalAcceptance> findByUserIdAndDocumentId(Long userId, Long documentId);
}
