package com.carrental.controller;

import com.carrental.service.ContractTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContractTemplateController {
    private final ContractTemplateService service;

    @GetMapping("/contract-templates")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS') or @rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<?> listTemplates() {
        List<Map<String, Object>> templates = service.listTemplates();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", templates.isEmpty() ? "No contract templates configured" : "Contract templates loaded successfully",
                "data", templates));
    }

    @PostMapping("/contract-templates")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> createTemplate(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(Map.of("success", true, "message", "Template created successfully", "data", service.createTemplate(body)));
    }

    @GetMapping("/contract-templates/system")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS') or @rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<?> listSystemTemplates() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "System contract templates loaded successfully",
                "data", service.listSystemTemplates()));
    }

    @PostMapping("/contract-templates/system/{code}/use")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> useSystemTemplate(@PathVariable String code) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Template selected successfully.",
                "data", service.useSystemTemplate(code)));
    }

    @GetMapping("/contract-templates/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS') or @rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<?> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "data", service.getTemplate(id)));
    }

    @PutMapping("/contract-templates/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Template updated successfully", "data", service.updateTemplate(id, body)));
    }

    @DeleteMapping("/contract-templates/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        service.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/contract-templates/{id}/upload-front", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> uploadFront(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Template front page uploaded successfully", "data", service.upload(id, file, true)));
    }

    @PostMapping(value = "/contract-templates/{id}/upload-back", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> uploadBack(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Template conditions page uploaded successfully", "data", service.upload(id, file, false)));
    }

    @GetMapping("/contract-templates/{id}/fields")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS') or @rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<?> listFields(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "data", service.listFields(id)));
    }

    @PostMapping("/contract-templates/{id}/fields")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> createField(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Field saved successfully", "data", service.createField(id, body)));
    }

    @PutMapping("/contract-templates/{id}/fields")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> replaceFields(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("fields");
        List<Map<String, Object>> fields = raw instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        return ResponseEntity.ok(Map.of("success", true, "message", "Field mapping saved successfully", "data", service.replaceFields(id, fields)));
    }

    @PutMapping("/contract-templates/{id}/fields/{fieldId}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> updateField(@PathVariable Long id, @PathVariable Long fieldId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Field saved successfully", "data", service.updateField(id, fieldId, body)));
    }

    @DeleteMapping("/contract-templates/{id}/fields/{fieldId}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Void> deleteField(@PathVariable Long id, @PathVariable Long fieldId) {
        service.deleteField(id, fieldId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contract-templates/{id}/set-default")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Default contract template updated successfully", "data", service.setDefault(id)));
    }

    @GetMapping("/contract-templates/{id}/preview-pdf")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS') or @rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<byte[]> previewPdf(@PathVariable Long id) {
        byte[] pdf = service.previewPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"contract-template-preview.pdf\"")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    @GetMapping("/contract-terms")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS') or @rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<?> listTerms() {
        return ResponseEntity.ok(Map.of("success", true, "data", service.listTerms()));
    }

    @PostMapping("/contract-terms")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> createTerms(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(Map.of("success", true, "message", "Contract terms saved successfully", "data", service.createTerms(body)));
    }

    @PutMapping("/contract-terms/{id}")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<?> updateTerms(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Contract terms saved successfully", "data", service.updateTerms(id, body)));
    }
}
