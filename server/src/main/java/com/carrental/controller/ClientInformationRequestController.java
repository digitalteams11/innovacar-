package com.carrental.controller;

import com.carrental.dto.clientinfo.ApproveClientInformationRequest;
import com.carrental.dto.clientinfo.ClientInformationRequestResponse;
import com.carrental.dto.clientinfo.CreateClientInformationRequestRequest;
import com.carrental.dto.clientinfo.ResendClientInformationRequest;
import com.carrental.service.ClientInformationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authenticated agency endpoints for the "client self-fill information"
 * workflow (MVP slice — see ClientInformationRequestService for scope notes).
 */
@RestController
@RequestMapping("/api/client-information-requests")
@RequiredArgsConstructor
public class ClientInformationRequestController {

    private final ClientInformationRequestService service;

    @PostMapping
    @PreAuthorize("@rolePermissionService.has('CREATE_CLIENT')")
    public ResponseEntity<ClientInformationRequestResponse> create(@Valid @RequestBody CreateClientInformationRequestRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping
    public ResponseEntity<List<ClientInformationRequestResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientInformationRequestResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDetail(id));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("@rolePermissionService.has('EDIT_CLIENT')")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        service.revoke(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@rolePermissionService.has('EDIT_CLIENT')")
    public ResponseEntity<ClientInformationRequestResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(service.reject(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@rolePermissionService.has('EDIT_CLIENT')")
    public ResponseEntity<ClientInformationRequestResponse> approve(
            @PathVariable Long id, @Valid @RequestBody ApproveClientInformationRequest req) {
        return ResponseEntity.ok(service.approve(id, req));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("@rolePermissionService.has('CREATE_CLIENT')")
    public ResponseEntity<ClientInformationRequestResponse> resend(
            @PathVariable Long id, @RequestBody(required = false) ResendClientInformationRequest req) {
        List<String> channels = req != null ? req.getChannels() : null;
        return ResponseEntity.ok(service.resend(id, channels));
    }

    @GetMapping("/{id}/delivery-status")
    public ResponseEntity<ClientInformationRequestResponse> deliveryStatus(@PathVariable Long id) {
        return ResponseEntity.ok(service.deliveryStatus(id));
    }
}
