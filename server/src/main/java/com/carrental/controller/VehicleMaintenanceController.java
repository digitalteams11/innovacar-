package com.carrental.controller;

import com.carrental.entity.MaintenanceStatus;
import com.carrental.service.VehicleMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class VehicleMaintenanceController {
    private final VehicleMaintenanceService maintenanceService;

    @GetMapping
    @PreAuthorize("@rolePermissionService.has('VIEW_MAINTENANCE')")
    public ResponseEntity<List<Map<String, Object>>> list(@RequestParam(required = false) Long vehicleId) {
        return ResponseEntity.ok(maintenanceService.list(vehicleId));
    }

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('MANAGE_MAINTENANCE')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maintenanceService.create(body));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@rolePermissionService.has('MANAGE_MAINTENANCE')")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(maintenanceService.updateStatus(
                id, MaintenanceStatus.valueOf(body.get("status").toUpperCase())));
    }
}
