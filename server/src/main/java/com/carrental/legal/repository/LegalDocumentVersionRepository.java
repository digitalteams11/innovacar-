package com.carrental.legal.repository;

import com.carrental.legal.entity.LegalDocumentStatus;
import com.carrental.legal.entity.LegalDocumentType;
import com.carrental.legal.entity.LegalDocumentVersion;
import com.carrental.legal.entity.LegalLocale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalDocumentVersionRepository extends JpaRepository<LegalDocumentVersion, Long> {

    Optional<LegalDocumentVersion> findByDocumentTypeAndLocaleAndStatus(
            LegalDocumentType documentType, LegalLocale locale, LegalDocumentStatus status);

    Optional<LegalDocumentVersion> findByDocumentTypeAndLocaleAndVersionNumber(
            LegalDocumentType documentType, LegalLocale locale, Integer versionNumber);

    List<LegalDocumentVersion> findAllByDocumentTypeAndLocaleOrderByVersionNumberDesc(
            LegalDocumentType documentType, LegalLocale locale);

    List<LegalDocumentVersion> findAllByDocumentTypeOrderByLocaleAscVersionNumberDesc(LegalDocumentType documentType);

    List<LegalDocumentVersion> findAllByStatusOrderByDocumentTypeAscLocaleAsc(LegalDocumentStatus status);

    int countByDocumentTypeAndLocale(LegalDocumentType documentType, LegalLocale locale);
}
