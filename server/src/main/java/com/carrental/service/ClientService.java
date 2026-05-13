package com.carrental.service;

import com.carrental.dto.client.CreateClientRequest;
import com.carrental.dto.client.UpdateClientRequest;
import com.carrental.dto.client.ClientResponse;
import com.carrental.entity.Client;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Client-management business logic.
 *
 * <p><strong>Tenant isolation:</strong> every query is scoped to the
 * {@code tenantId} extracted from the JWT via {@link TenantContext}.
 * A user of tenant A will always receive a 404 for clients that
 * belong to tenant B — preventing both data leakage and enumeration.
 *
 * <p><strong>Access policy (enforced at controller level):</strong>
 * Any authenticated user may read clients. Only ADMIN users may
 * create, update, or delete them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;

    // ── READ ─────────────────────────────────────────────────────────────────

    /**
     * Lists all clients for the caller's tenant.
     */
    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {
        Long tenantId = TenantContext.getCurrentTenantId();
        log.debug("Listing clients for tenant [{}]", tenantId);

        return clientRepository.findAllByTenantId(tenantId)
                .stream()
                .map(ClientResponse::from)
                .toList();
    }

    /**
     * Fetches a single client scoped to the caller's tenant.
     *
     * @throws ResourceNotFoundException if the client does not exist in this tenant
     */
    @Transactional(readOnly = true)
    public ClientResponse getClientById(Long id) {
        return ClientResponse.from(fetchClientInTenant(id));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Adds a new client to the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the tenant record cannot be found
     */
    @Transactional
    public ClientResponse createClient(CreateClientRequest request) {
        Long   tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant   = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + tenantId));

        Client client = clientRepository.save(Client.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .drivingLicense(request.getDrivingLicense())
                .tenant(tenant)
                .build());

        log.info("Created client [id={}] '{}' in tenant [{}]",
                client.getId(), client.getName(), tenantId);

        return ClientResponse.from(client);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields in {@code request} are applied.
     * ADMIN-only.
     *
     * @throws ResourceNotFoundException if the client is not found in this tenant
     */
    @Transactional
    public ClientResponse updateClient(Long id, UpdateClientRequest request) {
        Client client = fetchClientInTenant(id);

        if (StringUtils.hasText(request.getName())) {
            client.setName(request.getName());
        }
        if (StringUtils.hasText(request.getEmail())) {
            client.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getPhone())) {
            client.setPhone(request.getPhone());
        }
        if (StringUtils.hasText(request.getAddress())) {
            client.setAddress(request.getAddress());
        }
        if (StringUtils.hasText(request.getDrivingLicense())) {
            client.setDrivingLicense(request.getDrivingLicense());
        }

        Client saved = clientRepository.save(client);
        log.info("Updated client [id={}] in tenant [{}]", id, TenantContext.getCurrentTenantId());
        return ClientResponse.from(saved);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes a client from the caller's tenant. ADMIN-only.
     *
     * @throws ResourceNotFoundException if the client is not found in this tenant
     */
    @Transactional
    public void deleteClient(Long id) {
        Client client = fetchClientInTenant(id);
        clientRepository.delete(client);
        log.info("Deleted client [id={}] from tenant [{}]",
                id, TenantContext.getCurrentTenantId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tenant-scoped client lookup. Returns 404 for both missing and
     * cross-tenant clients so tenant B cannot discover tenant A's IDs.
     */
    private Client fetchClientInTenant(Long clientId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return clientRepository.findByIdAndTenantId(clientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId));
    }
}
