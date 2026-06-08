package com.carrental.repository;

import com.carrental.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
    List<SupportTicket> findByStatusOrderByCreatedAtDesc(String status);
    List<SupportTicket> findByPriorityOrderByCreatedAtDesc(String priority);
    List<SupportTicket> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    long countByStatus(String status);
}
