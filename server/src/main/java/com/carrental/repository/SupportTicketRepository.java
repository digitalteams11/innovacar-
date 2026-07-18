package com.carrental.repository;

import com.carrental.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
    List<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicket.Status status);
    List<SupportTicket> findByPriorityOrderByCreatedAtDesc(SupportTicket.Priority priority);
    List<SupportTicket> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<SupportTicket> findByTenantIdAndStatusOrderByCreatedAtDesc(Long tenantId, SupportTicket.Status status);
    long countByStatus(SupportTicket.Status status);
}
