package com.carrental.controller;

import com.carrental.dto.contract.AdditionalDriverDto;
import com.carrental.dto.contract.AdditionalDriverRequest;
import com.carrental.entity.AdditionalDriver;
import com.carrental.entity.Contract;
import com.carrental.entity.SignatureStatus;
import com.carrental.entity.Tenant;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.AdditionalDriverRepository;
import com.carrental.repository.ContractAuditLogRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.security.TenantContext;
import com.carrental.service.AdditionalDriverSigningService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers AdditionalDriverController's authenticated CRUD behavior: editing a
 * signed driver's identity vs. non-identity fields, delete confirmation for a
 * signed driver, duplicate/max-driver validation on create, and tenant
 * isolation (mirrors AnnouncementControllerTest's plain-constructor pattern —
 * no MockMvc/Spring context needed since @PreAuthorize is a proxy-level
 * concern tested separately).
 */
@ExtendWith(MockitoExtension.class)
class AdditionalDriverControllerTest {

    @Mock private AdditionalDriverRepository additionalDriverRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractAuditLogRepository contractAuditLogRepository;
    @Mock private AdditionalDriverSigningService signingService;

    private AdditionalDriverController controller;
    private Tenant tenant;
    private Contract contract;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
        controller = new AdditionalDriverController(
                additionalDriverRepository, contractRepository, contractAuditLogRepository, signingService);
        tenant = Tenant.builder().id(1L).name("Acme Rental").build();
        contract = Contract.builder().id(100L).tenant(tenant)
                .clientDriverLicense("MAIN-DL").clientPhone("+212600000000").build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AdditionalDriverRequest request(String fullName, String license, String phone) {
        AdditionalDriverRequest req = new AdditionalDriverRequest();
        req.setFullName(fullName);
        req.setDriverLicenseNumber(license);
        req.setPhone(phone);
        return req;
    }

    // ── 11. Editing a signed driver ─────────────────────────────────────────

    @Test
    void updatingIdentityFieldsOnASignedDriverIsRejected() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        AdditionalDriver signedDriver = AdditionalDriver.builder()
                .id(5L).fullName("Original Name").driverLicenseNumber("DL-ORIGINAL")
                .phone("+212611111111").signatureStatus(SignatureStatus.SIGNED)
                .contract(contract).build();
        when(additionalDriverRepository.findByIdAndContractId(5L, 100L)).thenReturn(Optional.of(signedDriver));

        AdditionalDriverRequest req = request("Changed Name", "DL-ORIGINAL", "+212611111111");

        assertThatThrownBy(() -> controller.update(100L, 5L, req))
                .isInstanceOf(IllegalStateException.class);
        verify(additionalDriverRepository, never()).save(any());
    }

    @Test
    void updatingNonIdentityFieldsOnASignedDriverIsAllowed() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        AdditionalDriver signedDriver = AdditionalDriver.builder()
                .id(6L).fullName("Same Name").driverLicenseNumber("DL-SAME")
                .phone("+212622222222").signatureStatus(SignatureStatus.SIGNED)
                .contract(contract).build();
        when(additionalDriverRepository.findByIdAndContractId(6L, 100L)).thenReturn(Optional.of(signedDriver));
        when(additionalDriverRepository.findAllByContractId(100L)).thenReturn(List.of(signedDriver));
        when(additionalDriverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Same identity fields, but change phone/email/signatureRequired — must be allowed.
        AdditionalDriverRequest req = request("Same Name", "DL-SAME", "+212699999999");
        req.setEmail("new-email@example.com");
        req.setSignatureRequired(false);

        ResponseEntity<AdditionalDriverDto> response = controller.update(100L, 6L, req);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(signedDriver.getPhone()).isEqualTo("+212699999999");
        assertThat(signedDriver.getEmail()).isEqualTo("new-email@example.com");
        assertThat(signedDriver.isSignatureRequired()).isFalse();
        assertThat(signedDriver.getFullName()).isEqualTo("Same Name");
        verify(additionalDriverRepository).save(signedDriver);
    }

    // ── 12. Deleting a signed driver ────────────────────────────────────────

    @Test
    void deletingASignedDriverWithoutConfirmIsRejected() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        AdditionalDriver signedDriver = AdditionalDriver.builder()
                .id(7L).fullName("Signed Driver").signatureStatus(SignatureStatus.SIGNED).contract(contract).build();
        when(additionalDriverRepository.findByIdAndContractId(7L, 100L)).thenReturn(Optional.of(signedDriver));

        assertThatThrownBy(() -> controller.delete(100L, 7L, false))
                .isInstanceOf(IllegalStateException.class);
        verify(additionalDriverRepository, never()).delete(any());
    }

    @Test
    void deletingASignedDriverWithConfirmSucceedsAndIsAudited() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        AdditionalDriver signedDriver = AdditionalDriver.builder()
                .id(8L).fullName("Signed Driver").signatureStatus(SignatureStatus.SIGNED).contract(contract).build();
        when(additionalDriverRepository.findByIdAndContractId(8L, 100L)).thenReturn(Optional.of(signedDriver));

        ResponseEntity<Void> response = controller.delete(100L, 8L, true);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(additionalDriverRepository).delete(signedDriver);
        verify(contractAuditLogRepository).save(any());
    }

    // ── 13. Tenant isolation ─────────────────────────────────────────────────

    @Test
    void aDriverFromAnotherTenantsContractCannotBeFetched() {
        // Tenant B's authenticated context — tenant A's contract row simply isn't found.
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(100L, 5L, request("X", "L", "P")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listingDriversForAnInaccessibleContractIs404NotForbidden() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.list(100L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── 15. Duplicate validation ─────────────────────────────────────────────

    @Test
    void creatingADriverDuplicatingAnotherDriversLicenseIsRejected() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        AdditionalDriver existing = AdditionalDriver.builder().id(1L).driverLicenseNumber("DUP-LICENSE").phone("+212600000001").build();
        when(additionalDriverRepository.findAllByContractId(100L)).thenReturn(List.of(existing));

        AdditionalDriverRequest req = request("New Driver", "DUP-LICENSE", "+212600000099");

        assertThatThrownBy(() -> controller.create(100L, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(additionalDriverRepository, never()).save(any());
    }

    @Test
    void creatingADriverDuplicatingAnotherDriversPhoneIsRejected() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        AdditionalDriver existing = AdditionalDriver.builder().id(1L).driverLicenseNumber("OTHER-LIC").phone("+212600000001").build();
        when(additionalDriverRepository.findAllByContractId(100L)).thenReturn(List.of(existing));

        AdditionalDriverRequest req = request("New Driver", "NEW-LIC", "+212600000001");

        assertThatThrownBy(() -> controller.create(100L, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creatingADriverDuplicatingTheMainClientIsRejectedUnlessExplicitlyAllowed() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        when(additionalDriverRepository.findAllByContractId(100L)).thenReturn(List.of());

        AdditionalDriverRequest req = request("Duplicate Of Main", "MAIN-DL", "+212600000000");
        assertThatThrownBy(() -> controller.create(100L, req)).isInstanceOf(IllegalArgumentException.class);

        req.setAllowDuplicateOfMainClient(true);
        when(additionalDriverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<AdditionalDriverDto> response = controller.create(100L, req);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ── 16. Max-drivers-per-contract limit ──────────────────────────────────

    @Test
    void creatingAnNinthDriverExceedsTheMaxOfEightAndIsRejected() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        List<AdditionalDriver> eightDrivers = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> AdditionalDriver.builder().id((long) i).driverLicenseNumber("L" + i).phone("P" + i).build())
                .toList();
        when(additionalDriverRepository.findAllByContractId(100L)).thenReturn(eightDrivers);

        AdditionalDriverRequest req = request("Ninth Driver", "L-NEW", "P-NEW");

        assertThatThrownBy(() -> controller.create(100L, req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(additionalDriverRepository, never()).save(any());
    }

    @Test
    void creatingUpToTheEighthDriverIsAllowed() {
        when(contractRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(contract));
        List<AdditionalDriver> sevenDrivers = java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> AdditionalDriver.builder().id((long) i).driverLicenseNumber("L" + i).phone("P" + i).build())
                .toList();
        when(additionalDriverRepository.findAllByContractId(100L)).thenReturn(sevenDrivers);
        when(additionalDriverRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdditionalDriverRequest req = request("Eighth Driver", "L-NEW", "P-NEW");

        ResponseEntity<AdditionalDriverDto> response = controller.create(100L, req);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(additionalDriverRepository, times(1)).save(any());
    }
}
