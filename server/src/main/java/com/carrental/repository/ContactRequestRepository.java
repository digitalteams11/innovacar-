package com.carrental.repository;

import com.carrental.entity.ContactRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {
    List<ContactRequest> findAllByOrderByCreatedAtDesc();
    List<ContactRequest> findByStatusOrderByCreatedAtDesc(String status);
}
