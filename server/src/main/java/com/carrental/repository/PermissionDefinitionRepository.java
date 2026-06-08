package com.carrental.repository;

import com.carrental.entity.PermissionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionDefinitionRepository extends JpaRepository<PermissionDefinition, Long> {
    Optional<PermissionDefinition> findByCode(String code);
}
