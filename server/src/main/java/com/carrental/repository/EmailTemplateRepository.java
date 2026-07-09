package com.carrental.repository;

import com.carrental.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    List<EmailTemplate> findAllByIsActiveTrue();
    List<EmailTemplate> findByType(String type);

    Optional<EmailTemplate> findByTemplateKeyAndLanguage(String templateKey, String language);
    Optional<EmailTemplate> findByTemplateKeyAndLanguageAndIsActiveTrue(String templateKey, String language);

    boolean existsByTemplateKey(String templateKey);
    boolean existsByTemplateKeyAndLanguage(String templateKey, String language);

    List<EmailTemplate> findAllByTemplateKey(String templateKey);

    long countBySystemDefaultTrue();
}
