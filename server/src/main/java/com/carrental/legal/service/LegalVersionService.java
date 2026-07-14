package com.carrental.legal.service;

import com.carrental.legal.audit.LegalAuditLogger;
import com.carrental.legal.dto.*;
import com.carrental.legal.entity.*;
import com.carrental.legal.exception.LegalDocumentNotFoundException;
import com.carrental.legal.exception.LegalDocumentStateException;
import com.carrental.legal.mapper.LegalDocumentMapper;
import com.carrental.legal.repository.LegalAcceptanceRepository;
import com.carrental.legal.repository.LegalDocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Super Admin authoring workflow: draft -> edit -> publish (which atomically
 * archives whatever was previously PUBLISHED for that documentType+locale).
 */
@Service
@RequiredArgsConstructor
public class LegalVersionService {

    private final LegalDocumentVersionRepository versionRepository;
    private final LegalAcceptanceRepository acceptanceRepository;
    private final LegalAuditLogger auditLogger;

    @Transactional
    public LegalDocumentVersionDto createDraft(CreateLegalDocumentVersionRequest request, Long adminUserId, String adminEmail) {
        LegalLocale locale = LegalLocale.fromCode(request.getLocale());
        int nextVersionNumber = versionRepository.countByDocumentTypeAndLocale(request.getDocumentType(), locale) + 1;

        LegalDocumentVersion draft = LegalDocumentVersion.builder()
                .documentType(request.getDocumentType())
                .locale(locale)
                .versionNumber(nextVersionNumber)
                .title(request.getTitle())
                .contentHtml(request.getContentHtml())
                .summaryOfChanges(request.getSummaryOfChanges())
                .material(request.isMaterial())
                .status(LegalDocumentStatus.DRAFT)
                .effectiveDate(request.getEffectiveDate())
                .createdByUserId(adminUserId)
                .createdByEmail(adminEmail)
                .build();

        LegalDocumentVersion saved = versionRepository.save(draft);
        auditLogger.documentDrafted(adminUserId, saved.getDocumentType(), locale.name(), saved.getVersionNumber());
        return LegalDocumentMapper.toDto(saved);
    }

    @Transactional
    public LegalDocumentVersionDto updateDraft(Long versionId, UpdateLegalDocumentVersionRequest request, Long adminUserId) {
        LegalDocumentVersion version = getOrThrow(versionId);
        if (version.getStatus() != LegalDocumentStatus.DRAFT) {
            throw new LegalDocumentStateException("Only a DRAFT version can be edited. Create a new draft instead.");
        }
        version.setTitle(request.getTitle());
        version.setContentHtml(request.getContentHtml());
        version.setSummaryOfChanges(request.getSummaryOfChanges());
        version.setMaterial(request.isMaterial());
        version.setEffectiveDate(request.getEffectiveDate());
        LegalDocumentVersion saved = versionRepository.save(version);
        auditLogger.documentUpdated(adminUserId, saved.getId());
        return LegalDocumentMapper.toDto(saved);
    }

    @Transactional
    public LegalDocumentVersionDto publish(Long versionId, Long adminUserId) {
        LegalDocumentVersion draft = getOrThrow(versionId);
        if (draft.getStatus() == LegalDocumentStatus.PUBLISHED) {
            throw new LegalDocumentStateException("This version is already published.");
        }
        if (draft.getStatus() == LegalDocumentStatus.ARCHIVED) {
            throw new LegalDocumentStateException("An archived version cannot be re-published. Create a new draft with its content instead.");
        }

        versionRepository.findByDocumentTypeAndLocaleAndStatus(
                        draft.getDocumentType(), draft.getLocale(), LegalDocumentStatus.PUBLISHED)
                .ifPresent(previouslyPublished -> {
                    previouslyPublished.setStatus(LegalDocumentStatus.ARCHIVED);
                    versionRepository.save(previouslyPublished);
                    auditLogger.documentArchived(adminUserId, previouslyPublished.getId());
                });

        draft.setStatus(LegalDocumentStatus.PUBLISHED);
        draft.setPublishedAt(LocalDateTime.now());
        if (draft.getEffectiveDate() == null) {
            draft.setEffectiveDate(java.time.LocalDate.now());
        }
        LegalDocumentVersion saved = versionRepository.save(draft);
        auditLogger.documentPublished(adminUserId, saved.getDocumentType(), saved.getLocale().name(),
                saved.getVersionNumber(), saved.isMaterial());
        return LegalDocumentMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<LegalDocumentVersionDto> listVersions(LegalDocumentType type, String localeCode) {
        LegalLocale locale = LegalLocale.fromCode(localeCode);
        return versionRepository.findAllByDocumentTypeAndLocaleOrderByVersionNumberDesc(type, locale)
                .stream().map(LegalDocumentMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LegalDocumentVersionDto> listAllVersionsForType(LegalDocumentType type) {
        return versionRepository.findAllByDocumentTypeOrderByLocaleAscVersionNumberDesc(type)
                .stream().map(LegalDocumentMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LegalDocumentVersionDto getVersionForAdmin(Long versionId) {
        return LegalDocumentMapper.toDto(getOrThrow(versionId));
    }

    @Transactional(readOnly = true)
    public LegalDocumentVersionStatsDto getAcceptanceStats(Long versionId) {
        LegalDocumentVersion version = getOrThrow(versionId);
        long count = acceptanceRepository.countByDocumentVersionId(versionId);
        return LegalDocumentVersionStatsDto.builder()
                .documentVersionId(versionId)
                .versionNumber(version.getVersionNumber())
                .locale(version.getLocale().name())
                .acceptanceCount(count)
                .build();
    }

    private LegalDocumentVersion getOrThrow(Long versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new LegalDocumentNotFoundException("Document version not found: " + versionId));
    }
}
