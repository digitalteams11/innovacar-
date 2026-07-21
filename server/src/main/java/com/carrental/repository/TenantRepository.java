package com.carrental.repository;

import com.carrental.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByEmail(String email);
    boolean existsByName(String name);
    boolean existsByEmail(String email);
    boolean existsByNameAndIdNot(String name, Long id);
    boolean existsByEmailAndIdNot(String email, Long id);

    /** Super Admin email-recipient directory search — matches agency name or contact email. */
    @Query("""
            SELECT t FROM Tenant t
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(t.name) LIKE CONCAT('%', :q, '%')
                OR LOWER(COALESCE(t.email, '')) LIKE CONCAT('%', :q, '%'))
            ORDER BY t.name ASC
            """)
    Page<Tenant> searchForEmailRecipients(@Param("q") String q, Pageable pageable);

    List<Tenant> findAllByStatusIgnoreCaseAndCancelEffectiveAtBefore(String status, LocalDateTime threshold);

    List<Tenant> findAllByStatusIgnoreCase(String status);

    long countByStatusIgnoreCase(String status);

    @Modifying
    @Transactional
    @Query("""
            update Tenant t
               set t.status = 'ACTIVE',
                   t.subscriptionActive = true,
                   t.trialStartDate = null,
                   t.trialEndDate = null
             where upper(t.status) = 'TRIAL'
               and upper(coalesce(t.planName, 'TRIAL')) <> 'TRIAL'
            """)
    int repairPaidPlansMarkedAsTrial();
}
