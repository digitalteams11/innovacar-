package com.carrental.legal.service;

import com.carrental.entity.User;
import com.carrental.legal.audit.LegalAuditLogger;
import com.carrental.legal.dto.PrivacyRequestCreateRequest;
import com.carrental.legal.dto.PrivacyRequestDto;
import com.carrental.legal.dto.PrivacyRequestStatusUpdateRequest;
import com.carrental.legal.entity.PrivacyRequest;
import com.carrental.legal.entity.PrivacyRequestStatus;
import com.carrental.legal.exception.PrivacyRequestNotFoundException;
import com.carrental.legal.mapper.PrivacyRequestMapper;
import com.carrental.legal.repository.PrivacyRequestRepository;
import com.carrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tracks data-subject rights requests (access, rectification, deletion,
 * export, objection, restriction — Law 09-08 / CNDP right-request handling).
 * Actual fulfilment of DELETION/EXPORT (purging or bundling a user's data)
 * is a separate, higher-risk operation outside this module's scope — this
 * service records and manages the request lifecycle; a Super Admin marks it
 * COMPLETED once fulfilment has happened through the appropriate account/data
 * tooling.
 */
@Service
@RequiredArgsConstructor
public class PrivacyRequestService {

    private final PrivacyRequestRepository privacyRequestRepository;
    private final UserRepository userRepository;
    private final LegalAuditLogger auditLogger;

    @Transactional
    public PrivacyRequestDto create(User user, PrivacyRequestCreateRequest request) {
        PrivacyRequest saved = privacyRequestRepository.save(PrivacyRequest.builder()
                .userId(user.getId())
                .tenantId(user.getTenant() != null ? user.getTenant().getId() : null)
                .requestType(request.getRequestType())
                .details(request.getDetails())
                .status(PrivacyRequestStatus.PENDING)
                .build());
        auditLogger.privacyRequestCreated(user.getId(), saved.getId(), request.getRequestType().name());
        return PrivacyRequestMapper.toDto(saved, user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<PrivacyRequestDto> getMine(Long userId) {
        String email = userRepository.findById(userId).map(User::getEmail).orElse(null);
        return privacyRequestRepository.findAllByUserIdOrderByRequestedAtDesc(userId)
                .stream().map(r -> PrivacyRequestMapper.toDto(r, email)).toList();
    }

    @Transactional(readOnly = true)
    public List<PrivacyRequestDto> getAllForAdmin() {
        return privacyRequestRepository.findAllByOrderByRequestedAtDesc().stream()
                .map(r -> PrivacyRequestMapper.toDto(r, emailFor(r.getUserId())))
                .toList();
    }

    @Transactional
    public PrivacyRequestDto updateStatus(Long requestId, PrivacyRequestStatusUpdateRequest request, Long adminUserId) {
        PrivacyRequest privacyRequest = privacyRequestRepository.findById(requestId)
                .orElseThrow(() -> new PrivacyRequestNotFoundException("Privacy request not found: " + requestId));
        privacyRequest.setStatus(request.getStatus());
        privacyRequest.setResolutionNotes(request.getResolutionNotes());
        if (request.getStatus() == PrivacyRequestStatus.COMPLETED || request.getStatus() == PrivacyRequestStatus.REJECTED) {
            privacyRequest.setResolvedAt(LocalDateTime.now());
            privacyRequest.setResolvedByUserId(adminUserId);
        }
        PrivacyRequest saved = privacyRequestRepository.save(privacyRequest);
        auditLogger.privacyRequestStatusChanged(adminUserId, requestId, request.getStatus().name());
        return PrivacyRequestMapper.toDto(saved, emailFor(saved.getUserId()));
    }

    private String emailFor(Long userId) {
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }
}
