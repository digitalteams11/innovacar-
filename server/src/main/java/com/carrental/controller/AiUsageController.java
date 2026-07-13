package com.carrental.controller;

import com.carrental.service.AiUsageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/ai/usage")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AiUsageController {

    private final AiUsageLogService aiUsageLogService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return success("Usage summary loaded successfully.", aiUsageLogService.summary());
    }

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<?> result = aiUsageLogService.listLogs(PageRequest.of(page, Math.min(size, 100)));
        return success("Usage logs loaded successfully.", pageBody(result));
    }

    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> errors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<?> result = aiUsageLogService.listErrors(PageRequest.of(page, Math.min(size, 100)));
        return success("Recent errors loaded successfully.", pageBody(result));
    }

    @DeleteMapping("/logs/{id}")
    public ResponseEntity<Map<String, Object>> deleteLog(@PathVariable Long id) {
        aiUsageLogService.deleteById(id);
        return success("Usage log deleted.", Map.of("id", id));
    }

    @DeleteMapping("/logs")
    public ResponseEntity<Map<String, Object>> clearLogs() {
        long deleted = aiUsageLogService.clearAll();
        return success("Usage logs cleared.", Map.of("deletedCount", deleted));
    }

    private Map<String, Object> pageBody(Page<?> result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result.getContent());
        data.put("page", result.getNumber());
        data.put("totalPages", result.getTotalPages());
        data.put("totalElements", result.getTotalElements());
        return data;
    }

    private ResponseEntity<Map<String, Object>> success(String message, Object data) {
        return ResponseEntity.ok(Map.of("success", true, "message", message, "data", data));
    }
}
