package com.carrental.controller;

import com.carrental.dto.search.GlobalSearchData;
import com.carrental.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    @GetMapping("/global")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> globalSearch(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "limit", required = false) Integer limit) {
        GlobalSearchData data = globalSearchService.search(query, limit);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Search results loaded successfully.",
                "data", data));
    }
}
