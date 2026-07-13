package com.carrental.repository;

import com.carrental.entity.AiAutomation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAutomationRepository extends JpaRepository<AiAutomation, Long> {

    Optional<AiAutomation> findByCode(String code);

    boolean existsByCode(String code);
}
