package com.carrental.repository;

import com.carrental.entity.AdditionalDriver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdditionalDriverRepository extends JpaRepository<AdditionalDriver, Long> {

    List<AdditionalDriver> findAllByContractId(Long contractId);

    long countByContractIdIn(List<Long> contractIds);

    void deleteAllByContractIdIn(List<Long> contractIds);

    /** Native bulk DELETE — bypasses @SQLRestriction on Contract (see PaymentRepository for details). */
    @Modifying
    @Query(value = "DELETE FROM contract_additional_drivers WHERE contract_id = :contractId", nativeQuery = true)
    int deleteNativeByContractId(@Param("contractId") Long contractId);
}
