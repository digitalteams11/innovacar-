package com.carrental.repository;

import com.carrental.entity.VehicleCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleConditionRepository extends JpaRepository<VehicleCondition, Long> {

    Optional<VehicleCondition> findByContractId(Long contractId);
}
