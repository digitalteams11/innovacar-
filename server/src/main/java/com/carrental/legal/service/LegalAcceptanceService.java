package com.carrental.legal.service;

import com.carrental.entity.User;
import com.carrental.legal.audit.LegalAuditLogger;
import com.carrental.legal.dto.AcceptDocumentRequest;
import com.carrental.legal.dto.LegalAcceptanceDto;
import com.carrental.legal.dto.PendingAcceptanceDto;
import com.carrental.legal.entity.LegalAcceptance;
import com.carrental.legal.entity.LegalDocumentStatus;
import com.carrental.legal.entity.LegalDocumentType;
import com.carrental.legal.entity.LegalDocumentVersion;
import com.carrental.legal.entity.LegalLocale;
import com.carrental.legal.exception.LegalDocumentNotFoundException;
import com.carrental.legal.mapper.LegalAcceptanceMapper;
import com.carrental.legal.repository.LegalAcceptanceRepository;
import com.carrental.legal.repository.LegalDocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Determines which published documents a user still needs to accept, and
 * records acceptance. A document becomes "pending" for a user when:
 *  - it has never been accepted by them at all (first-time), or
 *  - the currently published version is flagged {@code material} and is
 *    newer than the version they last accepted (re-acceptance).
 */
@Service
@RequiredArgsConstructor
public class LegalAcceptanceService {

    private final LegalDocumentVersionRepository versionRepository;
    private final LegalAcceptanceRepository acceptanceRepository;
    private final LegalAuditLogger auditLogger;

    @Transactional(readOnly = true)
    public List<PendingAcceptanceDto> getPendingAcceptances(User user) {
        LegalLocale locale = LegalLocale.fromCode(user.getLanguage());
        return Arrays.stream(LegalDocumentType.values())
                .map(type -> toPendingDtoIfApplicable(user, type, locale))
                .filter(Objects::nonNull)
                .toList();
    }

    private PendingAcceptanceDto toPendingDtoIfApplicable(User user, LegalDocumentType type, LegalLocale locale) {
        LegalDocumentVersion published = findPublishedWithFallback(type, locale);
        if (published == null) return null;

        Optional<LegalAcceptance> lastAcceptance =
                acceptanceRepository.findFirstByUserIdAndDocumentTypeOrderByVersionNumberDesc(user.getId(), type);

        boolean alreadyAcceptedThisVersion = acceptanceRepository
                .existsByUserIdAndDocumentVersionId(user.getId(), published.getId());
        if (alreadyAcceptedThisVersion) return null;

        boolean firstTime = lastAcceptance.isEmpty();
        if (!firstTime && !published.isMaterial()) {
            // A newer, non-material version was published (e.g. a typo fix) —
            // don't force a re-acceptance prompt for it.
            return null;
        }

        return PendingAcceptanceDto.builder()
                .documentType(type)
                .documentVersionId(published.getId())
                .versionNumber(published.getVersionNumber())
                .locale(published.getLocale().name())
                .title(published.getTitle())
                .summaryOfChanges(published.getSummaryOfChanges())
                .effectiveDate(published.getEffectiveDate())
                .firstTimeAcceptance(firstTime)
                .build();
    }

    @Transactional
    public LegalAcceptanceDto accept(User user, AcceptDocumentRequest request, String ipAddress, String userAgent) {
        LegalDocumentVersion version = versionRepository.findById(request.getDocumentVersionId())
                .orElseThrow(() -> new LegalDocumentNotFoundException(
                        "Document version not found: " + request.getDocumentVersionId()));
        if (version.getStatus() == LegalDocumentStatus.DRAFT) {
            throw new LegalDocumentNotFoundException("This document version is not published yet.");
        }

        if (acceptanceRepository.existsByUserIdAndDocumentVersionId(user.getId(), version.getId())) {
            // Idempotent: accepting the same version twice (double-click, retry) is a no-op, not an error.
            return acceptanceRepository.findFirstByUserIdAndDocumentTypeOrderByVersionNumberDesc(user.getId(), version.getDocumentType())
                    .map(LegalAcceptanceMapper::toDto)
                    .orElseThrow();
        }

        LegalAcceptance acceptance = LegalAcceptance.builder()
                .userId(user.getId())
                .tenantId(user.getTenant() != null ? user.getTenant().getId() : null)
                .documentType(version.getDocumentType())
                .locale(version.getLocale())
                .versionNumber(version.getVersionNumber())
                .documentVersionId(version.getId())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .captureContext(request.getCaptureContext() != null ? request.getCaptureContext() : "SETTINGS_PAGE")
                .build();

        LegalAcceptance saved = acceptanceRepository.save(acceptance);
        auditLogger.acceptanceRecorded(user.getId(), version.getDocumentType(), version.getVersionNumber(), acceptance.getCaptureContext());
        return LegalAcceptanceMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<LegalAcceptanceDto> getHistory(Long userId) {
        return acceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(userId)
                .stream().map(LegalAcceptanceMapper::toDto).toList();
    }

    private LegalDocumentVersion findPublishedWithFallback(LegalDocumentType type, LegalLocale locale) {
        for (LegalLocale candidate : locale.fallbackChain()) {
            Optional<LegalDocumentVersion> found = versionRepository
                    .findByDocumentTypeAndLocaleAndStatus(type, candidate, LegalDocumentStatus.PUBLISHED);
            if (found.isPresent()) return found.get();
        }
        return null;
    }
}
