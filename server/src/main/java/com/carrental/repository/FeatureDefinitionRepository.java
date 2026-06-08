package com.carrental.repository;

import com.carrental.entity.FeatureDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureDefinitionRepository extends JpaRepository<FeatureDefinition, Long> {
    Optional<FeatureDefinition> findByCode(String code);
    boolean existsByCode(String code);
    List<FeatureDefinition> findAllByActiveTrueOrderByNameAsc();
}
