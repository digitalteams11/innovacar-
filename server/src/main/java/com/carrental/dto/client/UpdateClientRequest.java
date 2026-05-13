package com.carrental.dto.client;

import lombok.Data;

/**
 * Request body for {@code PUT /api/clients/{id}} — partial update.
 * Any field left {@code null} is ignored by the service.
 */
@Data
public class UpdateClientRequest {

    private String name;

    private String email;

    private String phone;

    private String address;

    private String drivingLicense;
}
