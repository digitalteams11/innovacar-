package com.carrental.repository;

import com.carrental.entity.CancellationRequest;
import com.carrental.entity.CancellationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CancellationRequestRepository extends JpaRepository<CancellationRequest, Long> {
    List<CancellationRequest> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<CancellationRequest> findAllByTenantIdAndStatus(Long tenantId, CancellationRequestStatus status);
    List<CancellationRequest> findAllByStatusOrderByCreatedAtDesc(CancellationRequestStatus status);
    List<CancellationRequest> findAllByOrderByCreatedAtDesc();
}
