package com.carrental.repository;

import com.carrental.entity.Tenant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<Tenant> findAllByStatusIgnoreCaseAndCancelEffectiveAtBefore(String status, LocalDateTime threshold);

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
