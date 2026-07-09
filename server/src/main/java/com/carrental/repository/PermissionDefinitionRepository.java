package com.carrental.repository;

import com.carrental.entity.PermissionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionDefinitionRepository extends JpaRepository<PermissionDefinition, Long> {
    // Returns a list (not Optional) on purpose: if legacy duplicate rows exist for the
    // same code (e.g. seeded before the unique constraint existed, which ddl-auto=update
    // silently fails to retrofit onto a table that already has duplicates), an
    // Optional-returning query throws IncorrectResultSizeDataAccessException and 500s.
    List<PermissionDefinition> findAllByCode(String code);
}
