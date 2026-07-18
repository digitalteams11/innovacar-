package com.carrental.repository;

import com.carrental.entity.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {
    List<KnowledgeArticle> findByPublishedTrueOrderByCategoryAscTitleAsc();
    List<KnowledgeArticle> findByPublishedTrueAndIsFaqOrderByCategoryAscTitleAsc(Boolean isFaq);
    List<KnowledgeArticle> findByPublishedTrueAndCategoryOrderByTitleAsc(String category);
    java.util.Optional<KnowledgeArticle> findBySlugAndPublishedTrue(String slug);
}
