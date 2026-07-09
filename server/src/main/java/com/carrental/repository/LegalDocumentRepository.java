package com.carrental.repository;

import com.carrental.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {
    List<LegalDocument> findByActiveTrueOrderByTitleAsc();
}
