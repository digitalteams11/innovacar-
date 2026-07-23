package com.carrental.repository;

import com.carrental.entity.ClientInformationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientInformationRequestRepository extends JpaRepository<ClientInformationRequest, Long> {

    Optional<ClientInformationRequest> findByTokenHash(String tokenHash);

    Optional<ClientInformationRequest> findByIdAndTenantId(Long id, Long tenantId);

    List<ClientInformationRequest> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
