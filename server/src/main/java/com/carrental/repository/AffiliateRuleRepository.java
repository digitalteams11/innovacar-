package com.carrental.repository;

import com.carrental.entity.AffiliateRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AffiliateRuleRepository extends JpaRepository<AffiliateRule, Long> {
    Optional<AffiliateRule> findFirstByActiveTrueOrderByIdAsc();
}
