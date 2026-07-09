package com.carrental.repository;

import com.carrental.entity.ContractTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {
    @Query("""
            select t
            from ContractTemplate t
            where t.tenant.id = :tenantId
            order by t.defaultTemplate desc, t.updatedAt desc
            """)
    List<ContractTemplate> findAllByTenantIdOrderByDefaultTemplateDescUpdatedAtDesc(@Param("tenantId") Long tenantId);

    @Query("""
            select t
            from ContractTemplate t
            where t.id = :id
              and t.tenant.id = :tenantId
            """)
    Optional<ContractTemplate> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default Optional<ContractTemplate> findFirstByTenantIdAndDefaultTemplateTrueAndActiveTrueOrderByUpdatedAtDesc(Long tenantId) {
        return findActiveDefaultTemplates(tenantId).stream().findFirst();
    }

    @Query("""
            select t
            from ContractTemplate t
            where t.tenant.id = :tenantId
              and t.defaultTemplate = true
              and t.active = true
            order by t.updatedAt desc
            """)
    List<ContractTemplate> findActiveDefaultTemplates(@Param("tenantId") Long tenantId);

    @Query("""
            select t
            from ContractTemplate t
            where t.tenant.id = :tenantId
              and t.defaultTemplate = true
            """)
    List<ContractTemplate> findAllByTenantIdAndDefaultTemplateTrue(@Param("tenantId") Long tenantId);
}
