package com.carrental.service;

import com.carrental.entity.AdditionalDriver;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.SignatureStatus;
import com.carrental.entity.Tenant;
import com.carrental.repository.AdditionalDriverRepository;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractAuditLogRepository;
import com.carrental.repository.ContractDocumentRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.ReservationRepository;
import com.carrental.repository.TenantRepository;
import com.carrental.repository.VehicleConditionRepository;
import com.carrental.repository.VehicleRepository;
import com.carrental.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers ContractService.allRequiredSignaturesComplete / recheckSignatureCompletion
 * — the shared "is this contract fully signed" policy widened to also require
 * every signature-required additional driver (see plan §"Signature-policy /
 * completion logic"). Mirrors ContractServiceTest's mock/@InjectMocks setup.
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceSignatureCompletionTest {

    @Mock private ContractRepository contractRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AdditionalDriverRepository additionalDriverRepository;
    @Mock private ContractDocumentRepository contractDocumentRepository;
    @Mock private VehicleConditionRepository vehicleConditionRepository;
    @Mock private ContractAuditLogRepository contractAuditLogRepository;
    @Mock private DepositRepository depositRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PdfService pdfService;
    @Mock private NotificationService notificationService;
    @Mock private SseService sseService;
    @Mock private EmailService emailService;
    @Mock private PlatformEmailService platformEmailService;
    @Mock private DepositService depositService;
    @Mock private AvailabilityService availabilityService;
    @Mock private VehicleStatusSyncService vehicleStatusSyncService;

    @InjectMocks private ContractService contractService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
        tenant = Tenant.builder().id(1L).name("Acme Rental").build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Contract signedClientAndOwnerContract(ContractStatus status, AdditionalDriver... drivers) {
        List<AdditionalDriver> driverList = new ArrayList<>(List.of(drivers));
        return Contract.builder()
                .id(200L).tenant(tenant).status(status)
                .clientSignature("data:image/png;base64,AAAA")
                .ownerSignature("data:image/png;base64,BBBB")
                .additionalDrivers(driverList)
                .build();
    }

    private AdditionalDriver additionalDriver(Long id, boolean required, SignatureStatus status) {
        return AdditionalDriver.builder().id(id).fullName("Driver " + id)
                .signatureRequired(required).signatureStatus(status).build();
    }

    @Test
    void falseWhenClientAndOwnerSignedButARequiredDriverIsStillPending() {
        Contract contract = signedClientAndOwnerContract(ContractStatus.PARTIALLY_SIGNED,
                additionalDriver(1L, true, SignatureStatus.PENDING));

        assertThat(contractService.allRequiredSignaturesComplete(contract)).isFalse();
    }

    @Test
    void trueOnlyOnceEveryRequiredDriverHasSigned() {
        Contract contract = signedClientAndOwnerContract(ContractStatus.PARTIALLY_SIGNED,
                additionalDriver(1L, true, SignatureStatus.SIGNED),
                additionalDriver(2L, true, SignatureStatus.SIGNED));

        assertThat(contractService.allRequiredSignaturesComplete(contract)).isTrue();
    }

    @Test
    void aDriverWithSignatureNotRequiredNeverBlocksCompletion() {
        Contract contract = signedClientAndOwnerContract(ContractStatus.PARTIALLY_SIGNED,
                additionalDriver(1L, false, SignatureStatus.PENDING),
                additionalDriver(2L, true, SignatureStatus.SIGNED));

        assertThat(contractService.allRequiredSignaturesComplete(contract)).isTrue();
    }

    @Test
    void falseWhenClientOrOwnerHasNotSignedRegardlessOfDrivers() {
        Contract contract = Contract.builder()
                .id(201L).tenant(tenant).status(ContractStatus.DRAFT)
                .clientSignature(null)
                .ownerSignature("data:image/png;base64,BBBB")
                .additionalDrivers(new ArrayList<>())
                .build();

        assertThat(contractService.allRequiredSignaturesComplete(contract)).isFalse();
    }

    @Test
    void recheckSignatureCompletionFlipsStatusToActiveAndPersistsOnlyWhenChanged() {
        Contract contract = signedClientAndOwnerContract(ContractStatus.PARTIALLY_SIGNED,
                additionalDriver(1L, true, SignatureStatus.SIGNED));

        boolean changed = contractService.recheckSignatureCompletion(contract);

        assertThat(changed).isTrue();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        verify(contractRepository).save(contract);
    }

    @Test
    void recheckSignatureCompletionDoesNotPersistWhenNothingChanged() {
        // PENDING_SIGNATURE is the steady state repairSignedStatus itself assigns when
        // client-or-owner (but not full completion) is signed — so recomputing the same
        // inputs must be a true no-op (unlike the now-legacy PARTIALLY_SIGNED value).
        Contract contract = signedClientAndOwnerContract(ContractStatus.PENDING_SIGNATURE,
                additionalDriver(1L, true, SignatureStatus.PENDING));

        boolean changed = contractService.recheckSignatureCompletion(contract);

        assertThat(changed).isFalse();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.PENDING_SIGNATURE);
        verify(contractRepository, never()).save(contract);
    }
}
