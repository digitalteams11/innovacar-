package com.carrental.dto.client;

import com.carrental.entity.Client;
import lombok.Builder;
import lombok.Data;

/**
 * Read-only client projection returned by all client endpoints.
 */
@Data
@Builder
public class ClientResponse {

    private Long   id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String drivingLicense;
    private Long   tenantId;

    // ── Static factory ───────────────────────────────────────────────────────

    public static ClientResponse from(Client client) {
        return ClientResponse.builder()
                .id(client.getId())
                .name(client.getName())
                .email(client.getEmail())
                .phone(client.getPhone())
                .address(client.getAddress())
                .drivingLicense(client.getDrivingLicense())
                .tenantId(client.getTenant().getId())
                .build();
    }
}
