package com.carrental.repository;

import com.carrental.entity.SuperAdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuperAdminRoleRepository extends JpaRepository<SuperAdminRole, Long> {
    List<SuperAdminRole> findAllByCode(String code);
    Optional<SuperAdminRole> findFirstByCode(String code);
}
