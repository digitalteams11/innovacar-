package com.carrental.repository;

import com.carrental.entity.ContractDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {

    List<ContractDocument> findAllByContractId(Long contractId);

    void deleteAllByContractIdIn(List<Long> contractIds);

    /** Native bulk DELETE — bypasses @SQLRestriction on Contract (see PaymentRepository for details). */
    @Modifying
    @Query(value = "DELETE FROM contract_documents WHERE contract_id = :contractId", nativeQuery = true)
    int deleteNativeByContractId(@Param("contractId") Long contractId);
}
