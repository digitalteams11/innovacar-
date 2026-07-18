package com.carrental.controller;

import com.carrental.entity.KnowledgeArticle;
import com.carrental.repository.KnowledgeArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public, unauthenticated Help Center — separate from Support Tickets and
 * Contact Requests. Backed by {@link KnowledgeArticle}, which already had
 * everything a "help article" needs; no new entity was introduced.
 */
@RestController
@RequestMapping("/api/public/help")
@RequiredArgsConstructor
public class PublicHelpController {

    private final KnowledgeArticleRepository articleRepository;

    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        List<String> categories = articleRepository.findByPublishedTrueAndIsFaqOrderByCategoryAscTitleAsc(false)
                .stream()
                .map(KnowledgeArticle::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .collect(Collectors.toList());
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/articles")
    public ResponseEntity<List<Map<String, Object>>> articles(@RequestParam(required = false) String category) {
        List<KnowledgeArticle> articles = (category != null && !category.isBlank())
                ? articleRepository.findByPublishedTrueAndCategoryOrderByTitleAsc(category)
                : articleRepository.findByPublishedTrueAndIsFaqOrderByCategoryAscTitleAsc(false);
        List<Map<String, Object>> result = articles.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsFaq()))
                .map(this::summaryMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/articles/{slug}")
    public ResponseEntity<Map<String, Object>> article(@PathVariable String slug) {
        KnowledgeArticle article = articleRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> new IllegalArgumentException("Help article not found"));
        return ResponseEntity.ok(detailMap(article));
    }

    @GetMapping("/faq")
    public ResponseEntity<List<Map<String, Object>>> faq() {
        List<Map<String, Object>> result = articleRepository.findByPublishedTrueAndIsFaqOrderByCategoryAscTitleAsc(true)
                .stream()
                .map(this::detailMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> summaryMap(KnowledgeArticle article) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", article.getId());
        value.put("slug", article.getSlug());
        value.put("title", article.getTitle());
        value.put("category", article.getCategory());
        value.put("summary", article.getSummary());
        value.put("updatedAt", article.getUpdatedAt());
        return value;
    }

    private Map<String, Object> detailMap(KnowledgeArticle article) {
        Map<String, Object> value = summaryMap(article);
        value.put("content", article.getContent());
        return value;
    }
}
