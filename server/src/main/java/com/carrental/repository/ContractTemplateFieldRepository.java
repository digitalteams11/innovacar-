package com.carrental.repository;

import com.carrental.entity.ContractTemplateField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContractTemplateFieldRepository extends JpaRepository<ContractTemplateField, Long> {

    @Modifying
    @Query("delete from ContractTemplateField f where f.template.id = :templateId")
    void deleteByTemplateId(@Param("templateId") Long templateId);

    @Query("""
            select f
            from ContractTemplateField f
            where f.template.id = :templateId
            order by f.pageNumber asc, f.yPercent asc, f.xPercent asc
            """)
    List<ContractTemplateField> findFieldsForTemplate(@Param("templateId") Long templateId);

    @Query("""
            select f
            from ContractTemplateField f
            where f.id = :id
              and f.template.id = :templateId
            """)
    Optional<ContractTemplateField> findByIdAndTemplateId(@Param("id") Long id, @Param("templateId") Long templateId);
}
