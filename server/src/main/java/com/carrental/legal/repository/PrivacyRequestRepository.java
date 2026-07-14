package com.carrental.legal.repository;

import com.carrental.legal.entity.PrivacyRequest;
import com.carrental.legal.entity.PrivacyRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrivacyRequestRepository extends JpaRepository<PrivacyRequest, Long> {

    List<PrivacyRequest> findAllByUserIdOrderByRequestedAtDesc(Long userId);

    List<PrivacyRequest> findAllByOrderByRequestedAtDesc();

    List<PrivacyRequest> findAllByStatusOrderByRequestedAtAsc(PrivacyRequestStatus status);

    long countByStatus(PrivacyRequestStatus status);
}
