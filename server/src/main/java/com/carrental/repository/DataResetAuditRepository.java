package com.carrental.repository;

import com.carrental.entity.DataResetAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataResetAuditRepository extends JpaRepository<DataResetAuditLog, Long> {

    List<DataResetAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<DataResetAuditLog> findFirstByOrderByCreatedAtDesc();
}
