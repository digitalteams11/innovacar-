package com.carrental.repository;

import com.carrental.entity.SuperAdminPermissionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuperAdminPermissionDefinitionRepository extends JpaRepository<SuperAdminPermissionDefinition, Long> {
    List<SuperAdminPermissionDefinition> findAllByCode(String code);
}
