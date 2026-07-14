package com.carrental.legal.service;

import com.carrental.legal.dto.LegalDocumentSummaryDto;
import com.carrental.legal.dto.LegalDocumentVersionDto;
import com.carrental.legal.entity.LegalDocumentStatus;
import com.carrental.legal.entity.LegalDocumentType;
import com.carrental.legal.entity.LegalDocumentVersion;
import com.carrental.legal.entity.LegalLocale;
import com.carrental.legal.exception.LegalDocumentNotFoundException;
import com.carrental.legal.mapper.LegalDocumentMapper;
import com.carrental.legal.repository.LegalDocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Public-facing reads: "what is the current published document text a user
 * should see". Falls back across locales (see {@link LegalLocale#fallbackChain()})
 * so a document that hasn't been translated yet still renders instead of 404ing.
 */
@Service
@RequiredArgsConstructor
public class LegalDocumentService {

    private final LegalDocumentVersionRepository versionRepository;

    @Transactional(readOnly = true)
    public LegalDocumentVersionDto getPublished(LegalDocumentType type, String localeCode) {
        LegalLocale requested = LegalLocale.fromCode(localeCode);
        for (LegalLocale candidate : requested.fallbackChain()) {
            Optional<LegalDocumentVersion> found = versionRepository
                    .findByDocumentTypeAndLocaleAndStatus(type, candidate, LegalDocumentStatus.PUBLISHED);
            if (found.isPresent()) return LegalDocumentMapper.toDto(found.get());
        }
        throw new LegalDocumentNotFoundException(
                "No published version exists yet for " + type + ". A Super Admin must publish it first.");
    }

    @Transactional(readOnly = true)
    public List<LegalDocumentSummaryDto> listCurrentPublished(String localeCode) {
        LegalLocale requested = LegalLocale.fromCode(localeCode);
        return java.util.Arrays.stream(LegalDocumentType.values())
                .map(type -> {
                    for (LegalLocale candidate : requested.fallbackChain()) {
                        Optional<LegalDocumentVersion> found = versionRepository
                                .findByDocumentTypeAndLocaleAndStatus(type, candidate, LegalDocumentStatus.PUBLISHED);
                        if (found.isPresent()) return LegalDocumentMapper.toSummaryDto(found.get());
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public LegalDocumentVersionDto getVersion(LegalDocumentType type, String localeCode, Integer versionNumber) {
        LegalLocale locale = LegalLocale.fromCode(localeCode);
        LegalDocumentVersion version = versionRepository
                .findByDocumentTypeAndLocaleAndVersionNumber(type, locale, versionNumber)
                .orElseThrow(() -> new LegalDocumentNotFoundException(
                        "Version " + versionNumber + " not found for " + type + " (" + locale + ")"));
        // Drafts are internal — only Super Admin endpoints (which use LegalVersionService) may read them.
        if (version.getStatus() == LegalDocumentStatus.DRAFT) {
            throw new LegalDocumentNotFoundException("This document version is not published yet.");
        }
        return LegalDocumentMapper.toDto(version);
    }
}
