package com.carrental.controller;

import com.carrental.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/backups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BackupController {
    private final BackupService backupService;

    @GetMapping
    public List<Map<String, Object>> list() {
        return backupService.list();
    }

    @GetMapping("/configuration")
    public Map<String, Object> configuration() {
        return backupService.configuration();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create() {
        return ResponseEntity.status(HttpStatus.CREATED).body(backupService.createManual());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(backupService.downloadFileName(id)).build().toString())
                .body(backupService.download(id));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(
            @PathVariable Long id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(backupService.restore(id, request.get("confirmation")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        backupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
