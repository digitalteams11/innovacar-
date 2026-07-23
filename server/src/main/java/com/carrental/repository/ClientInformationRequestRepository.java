package com.carrental.repository;

import com.carrental.entity.ClientInfoRequestStatus;
import com.carrental.entity.ClientInformationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientInformationRequestRepository extends JpaRepository<ClientInformationRequest, Long> {

    Optional<ClientInformationRequest> findByTokenHash(String tokenHash);

    Optional<ClientInformationRequest> findByIdAndTenantId(Long id, Long tenantId);

    List<ClientInformationRequest> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    /** Still-open requests (never submitted/approved/terminated) past their expiry — for the expiry sweep job. */
    List<ClientInformationRequest> findAllByStatusInAndExpiresAtBefore(List<ClientInfoRequestStatus> statuses, LocalDateTime cutoff);

    /** Sent-but-never-opened requests older than the cutoff, eligible for a one-time "not opened yet" reminder. */
    List<ClientInformationRequest> findAllByStatusAndOpenedAtIsNullAndReminderNotOpenedSentAtIsNullAndCreatedAtBefore(
            ClientInfoRequestStatus status, LocalDateTime cutoff);

    /** Still-unsubmitted requests expiring soon, eligible for a one-time "expiring soon" reminder. */
    List<ClientInformationRequest> findAllByStatusInAndReminderExpirySentAtIsNullAndExpiresAtBefore(
            List<ClientInfoRequestStatus> statuses, LocalDateTime cutoff);
}
