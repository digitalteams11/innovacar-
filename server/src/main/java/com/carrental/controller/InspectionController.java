package com.carrental.controller;

import com.carrental.dto.inspection.CreateInspectionTokenRequest;
import com.carrental.dto.inspection.InspectionResponse;
import com.carrental.dto.inspection.InspectionMediaResponse;
import com.carrental.entity.InspectionMediaType;
import com.carrental.entity.InspectionType;
import com.carrental.service.InspectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.carrental.entity.InspectionMedia;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
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
public class InspectionController {
    private final InspectionService inspectionService;

    @PostMapping("/inspections/create-token")
    @PreAuthorize("@rolePermissionService.has('EDIT_RESERVATION') or @rolePermissionService.has('EDIT_CONTRACT')")
    public ResponseEntity<InspectionResponse> createToken(@Valid @RequestBody CreateInspectionTokenRequest request) {
        return ResponseEntity.ok(inspectionService.createToken(request));
    }

    @GetMapping("/inspections/token/{token}")
    public ResponseEntity<InspectionResponse> getByToken(@PathVariable String token) {
        return ResponseEntity.ok(inspectionService.getByToken(token));
    }

    @GetMapping("/public/inspections/{token}")
    public ResponseEntity<InspectionResponse> getPublicByToken(@PathVariable String token) {
        return ResponseEntity.ok(inspectionService.getByToken(token));
    }

    @PostMapping(value = "/inspections/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InspectionResponse> upload(
            @PathVariable Long id,
            @RequestParam String token,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "PHOTO") InspectionMediaType type,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) Integer duration) {
        return ResponseEntity.ok(inspectionService.upload(id, token, file, label, notes, type, duration));
    }

    @PostMapping(value = "/public/inspections/{token}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPublicMedia(
            @PathVariable String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("label") String label,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "inspectionType", required = false) InspectionType inspectionType) {
        InspectionMediaResponse media = inspectionService.uploadPublic(token, file, label, notes, inspectionType);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Inspection photo uploaded successfully",
                "data", Map.of(
                        "mediaId", media.getId(),
                        "label", media.getLabel(),
                        "url", media.getUrl(),
                        "fileUrl", media.getFileUrl(),
                        "fullUrl", media.getFullUrl(),
                        "uploadedAt", media.getUploadedAt()
                )
        ));
    }

    @GetMapping("/reservations/{id}/inspections")
    public ResponseEntity<List<InspectionResponse>> getReservationInspections(@PathVariable Long id) {
        return ResponseEntity.ok(inspectionService.getReservationInspections(id));
    }

    @GetMapping("/clients/{id}/inspections")
    public ResponseEntity<List<InspectionResponse>> getClientInspections(@PathVariable Long id) {
        return ResponseEntity.ok(inspectionService.getClientInspections(id));
    }

    @GetMapping("/contracts/{id}/inspections")
    public ResponseEntity<List<InspectionResponse>> getContractInspections(@PathVariable Long id) {
        return ResponseEntity.ok(inspectionService.getContractInspections(id));
    }

    @DeleteMapping("/inspections/expired-media")
    @PreAuthorize("@rolePermissionService.has('MANAGE_SETTINGS')")
    public ResponseEntity<Map<String, Object>> deleteExpiredMedia() {
        int count = inspectionService.deleteExpiredMedia();
        return ResponseEntity.ok(Map.of("deletedInspections", count));
    }

    @GetMapping("/public/inspection-media/{id}/{accessToken}")
    public ResponseEntity<Resource> getMedia(@PathVariable Long id, @PathVariable String accessToken) {
        InspectionMedia media = inspectionService.getMedia(id, accessToken);
        Resource resource = new FileSystemResource(java.nio.file.Path.of(media.getStoragePath()));
        String ct = (media.getContentType() != null && !media.getContentType().isBlank())
                ? media.getContentType() : "application/octet-stream";
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(ct))
                .body(resource);
    }
}
