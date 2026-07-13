package com.carrental.repository;

import com.carrental.entity.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {

    List<AiProvider> findAllByIsDeletedFalse();

    Optional<AiProvider> findByIsActiveTrueAndIsDeletedFalse();

    Optional<AiProvider> findByIsFallbackTrueAndIsDeletedFalse();

    boolean existsByIsDeletedFalse();
}
