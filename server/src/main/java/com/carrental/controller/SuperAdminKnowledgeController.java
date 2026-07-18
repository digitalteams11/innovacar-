package com.carrental.controller;

import com.carrental.entity.KnowledgeArticle;
import com.carrental.repository.KnowledgeArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Super Admin CRUD for Help Center articles (and FAQ entries, via the
 * {@code isFaq} flag). Category stays a free-text field on the article, same
 * as before this refactor — no separate category table.
 */
@RestController
@RequestMapping("/api/super-admin/help/articles")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminKnowledgeController {

    private final KnowledgeArticleRepository articleRepository;

    @GetMapping
    public ResponseEntity<List<KnowledgeArticle>> list() {
        return ResponseEntity.ok(articleRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<KnowledgeArticle> create(@RequestBody KnowledgeArticle article) {
        if (article.getSlug() == null || article.getSlug().isBlank()) {
            throw new IllegalArgumentException("A slug is required");
        }
        article.setId(null);
        return ResponseEntity.ok(articleRepository.save(article));
    }

    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeArticle> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Help article not found"));
        if (body.containsKey("title")) article.setTitle((String) body.get("title"));
        if (body.containsKey("category")) article.setCategory((String) body.get("category"));
        if (body.containsKey("summary")) article.setSummary((String) body.get("summary"));
        if (body.containsKey("content")) article.setContent((String) body.get("content"));
        if (body.containsKey("slug")) article.setSlug((String) body.get("slug"));
        if (body.containsKey("published")) article.setPublished((Boolean) body.get("published"));
        if (body.containsKey("isFaq")) article.setIsFaq((Boolean) body.get("isFaq"));
        return ResponseEntity.ok(articleRepository.save(article));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!articleRepository.existsById(id)) {
            throw new IllegalArgumentException("Help article not found");
        }
        articleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
