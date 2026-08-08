package com.carrental.service;

import com.carrental.dto.contract.ContractResponse;
import com.carrental.dto.contract.CreateContractRequest;
import com.carrental.dto.contract.PublicContractResponse;
import com.carrental.dto.contract.UpdateContractRequest;
import com.carrental.entity.Client;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
import com.carrental.entity.Invoice;
import com.carrental.entity.InvoiceStatus;
import com.carrental.entity.Reservation;
import com.carrental.entity.ReservationSource;
import com.carrental.entity.ReservationStatus;
import com.carrental.entity.Tenant;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.repository.AdditionalDriverRepository;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractAuditLogRepository;
import com.carrental.repository.ContractDocumentRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.DepositRepository;
import com.carrental.repository.InvoiceRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

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
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceService invoiceService;
    @Mock private com.carrental.repository.ContractExtensionRepository contractExtensionRepository;
    @Mock private PaymentService paymentService;
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
    private Client client;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(1L);
        tenant = Tenant.builder().id(1L).name("Acme Rental").build();
        client = Client.builder()
                .id(2L).name("Sara Client").email("sara@example.com")
                .drivingLicense("DL-100").tenant(tenant).build();
        vehicle = Vehicle.builder()
                .id(3L).marque("Dacia Duster").plate("123-A-45")
                .prixJour(new BigDecimal("300.00"))
                .depositAmount(new BigDecimal("1500.00"))
                .statut(VehicleStatus.AVAILABLE).tenant(tenant).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void directContractUsesLinkedClientAndVehicleAndStartsRental() {
        CreateContractRequest request = new CreateContractRequest();
        request.setClientId(client.getId());
        request.setVehicleId(vehicle.getId());
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(3));
        request.setPickupTime(LocalTime.of(9, 0));
        request.setReturnTime(LocalTime.of(18, 0));

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(clientRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.of(client));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(3L, 1L)).thenReturn(Optional.of(vehicle));
        when(availabilityService.isVehicleAvailable(
                3L, request.getStartDate(), request.getPickupTime(), request.getEndDate(), request.getReturnTime(), null))
                .thenReturn(true);
        when(contractRepository.existsByContractNumberIncludingDeleted(any())).thenReturn(false);
        when(reservationRepository.save(any())).thenAnswer(invocation -> {
            Reservation value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(40L);
            }
            return value;
        });
        when(contractRepository.save(any())).thenAnswer(invocation -> {
            Contract value = invocation.getArgument(0);
            value.setId(10L);
            return value;
        });

        ContractResponse response = contractService.createContract(request);

        assertThat(response.getStatus()).isEqualTo(ContractStatus.DRAFT);
        assertThat(response.getClientFullName()).isEqualTo("Sara Client");
        assertThat(response.getClientDriverLicense()).isEqualTo("DL-100");
        assertThat(response.getTotalPrice()).isEqualByComparingTo("900.00");
        assertThat(response.getReservationId()).isEqualTo(40L);
        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.RESERVED);
        verify(vehicleRepository).save(vehicle);
        verify(reservationRepository, times(2)).save(argThat(reservation ->
                reservation.getStatus() == ReservationStatus.CONFIRMED
                        && reservation.getSource() == ReservationSource.AUTO_FROM_CONTRACT));
    }

    @Test
    void newContractSnapshotsVehicleDocumentPresenceFromExpiryDates() {
        vehicle.setInsuranceExpiration(LocalDate.now().plusMonths(6));
        vehicle.setTechnicalInspectionExpiration(LocalDate.now().plusMonths(3));
        // licenseExpiryDate, vignetteExpiration and circulationAuthorizationExpiryDate
        // are deliberately left unset — must snapshot as "not present".

        CreateContractRequest request = new CreateContractRequest();
        request.setClientId(client.getId());
        request.setVehicleId(vehicle.getId());
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(3));
        request.setPickupTime(LocalTime.of(9, 0));
        request.setReturnTime(LocalTime.of(18, 0));

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(clientRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.of(client));
        when(vehicleRepository.findByIdAndTenantIdForUpdate(3L, 1L)).thenReturn(Optional.of(vehicle));
        when(availabilityService.isVehicleAvailable(
                3L, request.getStartDate(), request.getPickupTime(), request.getEndDate(), request.getReturnTime(), null))
                .thenReturn(true);
        when(contractRepository.existsByContractNumberIncludingDeleted(any())).thenReturn(false);
        when(reservationRepository.save(any())).thenAnswer(invocation -> {
            Reservation value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(41L);
            return value;
        });
        when(contractRepository.save(any())).thenAnswer(invocation -> {
            Contract value = invocation.getArgument(0);
            value.setId(11L);
            return value;
        });

        ContractResponse response = contractService.createContract(request);

        assertThat(response.getDocumentAssurance()).isTrue();
        assertThat(response.getDocumentVisiteTechnique()).isTrue();
        assertThat(response.getDocumentCarteGrise()).isFalse();
        assertThat(response.getDocumentVignette()).isFalse();
        assertThat(response.getDocumentAutorisationCirculation()).isFalse();
    }

    @Test
    void convertingReservationMakesItReadOnlyAndStartsRental() {
        Reservation reservation = Reservation.builder()
                .id(20L).tenant(tenant).client(client).vehicle(vehicle)
                .dateStart(LocalDate.now().plusDays(1))
                .dateEnd(LocalDate.now().plusDays(3))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(18, 0))
                .totalPrice(new BigDecimal("900.00"))
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(reservationRepository.findByIdAndTenantId(20L, 1L)).thenReturn(Optional.of(reservation));
        when(contractRepository.existsByContractNumberIncludingDeleted(any())).thenReturn(false);
        when(contractRepository.save(any())).thenAnswer(invocation -> {
            Contract value = invocation.getArgument(0);
            value.setId(30L);
            return value;
        });

        ContractService.FromReservationResult result = contractService.createFromReservation(20L);
        ContractResponse response = result.contract();

        assertThat(result.alreadyExisted()).isFalse();
        assertThat(response.getReservationId()).isEqualTo(20L);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONVERTED_TO_CONTRACT);
        assertThat(reservation.getContract()).isNotNull();
        assertThat(vehicle.getStatut()).isEqualTo(VehicleStatus.RENTED);
        verify(reservationRepository).save(reservation);
        verify(vehicleRepository).save(vehicle);
    }

    /**
     * Regression test for the production bug where every public contract PDF
     * download failed: generateContractPdf(Long) loads the contract via
     * fetchContractInTenant, which reads TenantContext.getCurrentTenantId() —
     * always null for a public/anonymous request (JwtAuthenticationFilter
     * skips "/api/public/**" entirely, so TenantContext is never populated).
     * generateContractPdfPublic must not depend on TenantContext at all.
     */
    @Test
    void generateContractPdfPublicWorksWithoutTenantContext() {
        TenantContext.clear();
        Contract contract = Contract.builder()
                .id(50L).contractNumber("CTR-2026-00050").tenant(tenant)
                .qrToken("tok-abc123").build();

        when(contractRepository.findById(50L)).thenReturn(Optional.of(contract));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(depositRepository.findByContractId(50L)).thenReturn(Optional.empty());
        byte[] expectedPdf = "PDF-BYTES".getBytes();
        when(pdfService.generateContractPdf(contract, tenant, null)).thenReturn(expectedPdf);

        byte[] pdf = contractService.generateContractPdfPublic(50L);

        assertThat(pdf).isEqualTo(expectedPdf);
    }

    /**
     * Regression test for the bug where PUT /contracts/{id} let a caller
     * directly overwrite paidAmount/remainingAmount via UpdateContractRequest
     * fields, bypassing the real Payment ledger entirely — those two fields
     * must be silently ignored, and the contract's financials must instead
     * be re-derived from actual Payment rows via
     * PaymentService#recalculateContractFinancials.
     */
    @Test
    void updateContract_ignoresClientSuppliedPaidAndRemainingAmount() {
        Contract contract = Contract.builder()
                .id(90L).contractNumber("CTR-2026-00090").tenant(tenant)
                .totalPrice(new BigDecimal("5000"))
                .paidAmount(new BigDecimal("2000"))
                .remainingAmount(new BigDecimal("3000"))
                .build();
        when(contractRepository.findByIdAndTenantId(90L, 1L)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        // Simulate what the real PaymentService would do: derive from actual
        // payments (here: still 2000 paid) regardless of the bogus request values below.
        doAnswer(inv -> {
            Contract c = inv.getArgument(0);
            c.setPaidAmount(new BigDecimal("2000"));
            c.setRemainingAmount(new BigDecimal("3000"));
            return null;
        }).when(paymentService).recalculateContractFinancials(contract);

        UpdateContractRequest request = new UpdateContractRequest();
        // A malicious/buggy caller tries to claim the contract is fully paid
        // and owes nothing — this must have zero effect on the saved entity.
        request.setPaidAmount(new BigDecimal("5000"));
        request.setRemainingAmount(BigDecimal.ZERO);

        ContractResponse response = contractService.updateContract(90L, request);

        assertThat(response.getPaidAmount()).isEqualByComparingTo("2000");
        assertThat(response.getRemainingAmount()).isEqualByComparingTo("3000");
        verify(paymentService).recalculateContractFinancials(contract);
    }

    /**
     * A fully signed contract's PDF is a legal document, frozen at signing
     * time — regenerateContractPdf must refuse to overwrite it rather than
     * silently applying whatever the agency's branding looks like today
     * (e.g. a replaced stamp) to a document the client already signed.
     */
    @Test
    void regenerateContractPdf_refusesToOverwriteFullySignedContract() {
        Contract contract = Contract.builder()
                .id(80L).contractNumber("CTR-2026-00080").tenant(tenant)
                .clientSignature("data:image/png;base64,AAAA")
                .ownerSignature("data:image/png;base64,BBBB")
                .pdfUrl("/uploads/contracts/CTR-2026-00080.pdf")
                .build();
        when(contractRepository.findByIdAndTenantId(80L, 1L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> contractService.regenerateContractPdf(80L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fully signed");

        verify(pdfService, never()).generateContractPdf(any(), any(), any());
        verify(contractRepository, never()).save(any());
    }

    /** A draft/unsigned contract must still regenerate normally — only a fully signed one is locked. */
    @Test
    void regenerateContractPdf_stillWorksForUnsignedDraftContract() {
        Contract contract = Contract.builder()
                .id(81L).contractNumber("CTR-2026-00081").tenant(tenant)
                .build();
        when(contractRepository.findByIdAndTenantId(81L, 1L)).thenReturn(Optional.of(contract));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(depositRepository.findByContractId(81L)).thenReturn(Optional.empty());
        byte[] freshPdf = "FRESH-PDF".getBytes();
        when(pdfService.generateContractPdf(contract, tenant, null)).thenReturn(freshPdf);
        when(pdfService.saveContractPdf(contract, freshPdf)).thenReturn("/uploads/contracts/CTR-2026-00081.pdf");
        when(contractRepository.save(contract)).thenReturn(contract);

        contractService.regenerateContractPdf(81L);

        verify(pdfService).generateContractPdf(contract, tenant, null);
        verify(contractRepository).save(contract);
    }

    /**
     * Regression test for the production bug where the public contract page's
     * "Download Signed Contract" button called the AUTHENTICATED admin
     * endpoint (contract.getPdfUrl() == "/api/contracts/{id}/pdf-file",
     * @PreAuthorize VIEW_CONTRACTS) and got 401/403 — the public response
     * must only ever expose the public, token-scoped PDF endpoint.
     */
    @Test
    void publicContractResponseExposesPublicPdfUrlNotTheAuthenticatedOne() {
        Contract contract = Contract.builder()
                .id(60L).contractNumber("CTR-2026-00060").tenant(tenant)
                .qrToken("tok-xyz789")
                .pdfUrl("/api/contracts/60/pdf-file")
                .build();

        when(contractRepository.findByQrToken("tok-xyz789")).thenReturn(Optional.of(contract));
        when(depositRepository.findByContractId(60L)).thenReturn(Optional.empty());

        PublicContractResponse response = contractService.getPublicContract("tok-xyz789");

        assertThat(response.getPdfUrl())
                .isEqualTo("/api/public/contracts/60/tok-xyz789/pdf")
                .isNotEqualTo(contract.getPdfUrl());
    }

    /**
     * Business decision: mileage was removed entirely from the contract module
     * (form/PDF/details/DTOs) — this is a compile-time guarantee (the fields
     * genuinely don't exist on Contract/ContractResponse/PublicContractResponse
     * any more), but reflection makes the intent explicit and catches anyone
     * re-adding a mileage field to the Contract entity by mistake later.
     */
    @Test
    void contractEntityHasNoMileageFields() {
        for (String name : new String[]{"mileageStart", "mileageEnd", "allowedMileage", "extraMileageCost"}) {
            assertThat(java.util.Arrays.stream(Contract.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                    .as("Contract should not declare a '%s' field — mileage was removed from the contract module", name)
                    .doesNotContain(name);
        }
    }

    /**
     * The vehicle's own fleet-mileage tracker (Vehicle.mileageCurrent) must be
     * completely unaffected by the contract-mileage removal — this is the
     * explicit "do not touch vehicle/maintenance mileage" requirement.
     */
    @Test
    void vehicleEntityStillHasFleetMileageField() {
        assertThat(java.util.Arrays.stream(Vehicle.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .as("Vehicle.mileageCurrent must remain — fleet mileage tracking is a separate concern from the contract")
                .contains("mileageCurrent");
    }

    /**
     * Regression test for the return-inspection flow: the odometer reading
     * captured in the "Vehicle Return Inspection" modal must still update the
     * vehicle's fleet mileage record, even though the contract itself no
     * longer stores or exposes mileage at all.
     */
    @Test
    void returnInspectionUpdatesVehicleFleetMileageButNotTheContract() {
        Contract contract = Contract.builder()
                .id(70L).contractNumber("CTR-2026-00070").tenant(tenant).vehicle(vehicle)
                .fuelLevelStart("FULL").status(ContractStatus.ACTIVE)
                .build();
        when(contractRepository.findByIdAndTenantId(70L, 1L)).thenReturn(Optional.of(contract));
        when(vehicleRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(vehicle));
        when(contractRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.carrental.dto.contract.ReturnInspectionRequest req = new com.carrental.dto.contract.ReturnInspectionRequest();
        req.setFuelLevelEnd("HALF");
        req.setMileageEnd(15000);

        contractService.processReturnInspection(70L, req);

        assertThat(vehicle.getMileageCurrent()).isEqualTo(15000);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.COMPLETED);
    }

    // ── Public signature link: expiry / already-signed ─────────────────────

    @Test
    void expiredSignatureLinkIsRejectedBeforeAnythingElse() {
        Contract contract = Contract.builder()
                .id(80L).contractNumber("CTR-2026-00080").tenant(tenant)
                .qrToken("tok-expired")
                .qrTokenExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(contractRepository.findByQrToken("tok-expired")).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> contractService.getPublicContract("tok-expired"))
                .isInstanceOf(com.carrental.exception.SignatureLinkException.class)
                .satisfies(ex -> assertThat(((com.carrental.exception.SignatureLinkException) ex).getErrorCode())
                        .isEqualTo("SIGNATURE_LINK_EXPIRED"));
    }

    @Test
    void signingAgainstAnExpiredLinkIsRejected() {
        Contract contract = Contract.builder()
                .id(81L).contractNumber("CTR-2026-00081").tenant(tenant)
                .qrToken("tok-expired-sign")
                .qrTokenExpiresAt(LocalDateTime.now().minusDays(1))
                .ownerSignature("data:image/png;base64,agency")
                .build();
        when(contractRepository.findByQrToken("tok-expired-sign")).thenReturn(Optional.of(contract));

        com.carrental.dto.contract.ContractSignatureRequest request = new com.carrental.dto.contract.ContractSignatureRequest();
        request.setSignatureData("data:image/png;base64,client");
        request.setSignerType(com.carrental.dto.contract.ContractSignatureRequest.SignerType.CLIENT);

        assertThatThrownBy(() -> contractService.signPublicContract("tok-expired-sign", request))
                .isInstanceOf(com.carrental.exception.SignatureLinkException.class)
                .satisfies(ex -> assertThat(((com.carrental.exception.SignatureLinkException) ex).getErrorCode())
                        .isEqualTo("SIGNATURE_LINK_EXPIRED"));
    }

    @Test
    void reSigningAnAlreadyFullySignedContractIsRejectedWithDedicatedCode() {
        Contract contract = Contract.builder()
                .id(82L).contractNumber("CTR-2026-00082").tenant(tenant)
                .qrToken("tok-already-signed")
                .qrTokenExpiresAt(LocalDateTime.now().plusDays(29))
                .ownerSignature("data:image/png;base64,agency")
                .clientSignature("data:image/png;base64,client")
                .build();
        when(contractRepository.findByQrToken("tok-already-signed")).thenReturn(Optional.of(contract));

        com.carrental.dto.contract.ContractSignatureRequest request = new com.carrental.dto.contract.ContractSignatureRequest();
        request.setSignatureData("data:image/png;base64,client-again");
        request.setSignerType(com.carrental.dto.contract.ContractSignatureRequest.SignerType.CLIENT);

        assertThatThrownBy(() -> contractService.signPublicContract("tok-already-signed", request))
                .isInstanceOf(com.carrental.exception.SignatureLinkException.class)
                .satisfies(ex -> assertThat(((com.carrental.exception.SignatureLinkException) ex).getErrorCode())
                        .isEqualTo("CONTRACT_ALREADY_SIGNED"));
    }

    @Test
    void validNonExpiredLinkLoadsSuccessfully() {
        Contract contract = Contract.builder()
                .id(83L).contractNumber("CTR-2026-00083").tenant(tenant)
                .qrToken("tok-valid")
                .qrTokenExpiresAt(LocalDateTime.now().plusDays(10))
                .build();
        when(contractRepository.findByQrToken("tok-valid")).thenReturn(Optional.of(contract));

        PublicContractResponse response = contractService.getPublicContract("tok-valid");

        assertThat(response.getContractNumber()).isEqualTo("CTR-2026-00083");
    }

    @Test
    void nullExpiryNeverExpiresAnOlderLinkThatPredatesTheExpiryFeature() {
        Contract contract = Contract.builder()
                .id(84L).contractNumber("CTR-2026-00084").tenant(tenant)
                .qrToken("tok-legacy")
                .qrTokenExpiresAt(null)
                .build();
        when(contractRepository.findByQrToken("tok-legacy")).thenReturn(Optional.of(contract));

        PublicContractResponse response = contractService.getPublicContract("tok-legacy");

        assertThat(response.getContractNumber()).isEqualTo("CTR-2026-00084");
    }

    // ── cancelContract: linked invoices must stay in sync, without hiding settled money ──

    @Test
    void cancelContract_cancelsUnsettledInvoiceButLeavesPaidInvoiceUntouched() {
        Contract contract = Contract.builder()
                .id(90L).contractNumber("CTR-2026-00090").tenant(tenant)
                .status(ContractStatus.ACTIVE)
                .build();
        Invoice pendingInvoice = Invoice.builder()
                .id(200L).tenant(tenant).contract(contract).status(InvoiceStatus.PENDING).build();
        Invoice paidInvoice = Invoice.builder()
                .id(201L).tenant(tenant).contract(contract).status(InvoiceStatus.PAID).build();

        when(contractRepository.findByIdAndTenantId(90L, 1L)).thenReturn(Optional.of(contract));
        when(invoiceRepository.findAllByTenantIdAndContractId(1L, 90L))
                .thenReturn(java.util.List.of(pendingInvoice, paidInvoice));
        when(contractRepository.save(any(Contract.class))).thenAnswer(i -> i.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        contractService.cancelContract(90L);

        assertThat(pendingInvoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(paidInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        verify(invoiceRepository, times(1)).save(pendingInvoice);
        verify(invoiceRepository, never()).save(paidInvoice);
    }

    // ── extendContract: "Prolonger la location" ─────────────────────────────

    @Test
    void extendContract_addsDaysAndAmountWithoutTouchingPaymentHistory() {
        Contract contract = Contract.builder()
                .id(95L).contractNumber("CTR-2026-00095").tenant(tenant)
                .status(ContractStatus.ACTIVE)
                .vehicle(vehicle)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(6))
                .rentalDays(5)
                .dailyPrice(new BigDecimal("1000.00"))
                .totalPrice(new BigDecimal("5000.00"))
                .build();

        when(contractRepository.findByIdAndTenantId(95L, 1L)).thenReturn(Optional.of(contract));
        when(availabilityService.isVehicleAvailable(eq(vehicle.getId()), any(), any(), any(), any(), any(), eq(95L)))
                .thenReturn(true);
        when(contractRepository.save(any(Contract.class))).thenAnswer(i -> i.getArgument(0));
        when(contractExtensionRepository.save(any(com.carrental.entity.ContractExtension.class)))
                .thenAnswer(i -> i.getArgument(0));

        com.carrental.dto.contract.ExtendContractRequest request = new com.carrental.dto.contract.ExtendContractRequest();
        request.setAdditionalDays(2);
        request.setReason("Client wants 2 more days");

        com.carrental.dto.contract.ExtendContractResponse response = contractService.extendContract(95L, request);

        assertThat(contract.getRentalDays()).isEqualTo(7);
        assertThat(contract.getTotalPrice()).isEqualByComparingTo("7000.00");
        assertThat(contract.getEndDate()).isEqualTo(LocalDate.now().plusDays(8));
        assertThat(response.getExtension().getAdditionalDays()).isEqualTo(2);
        assertThat(response.getExtension().getAdditionalAmount()).isEqualByComparingTo("2000.00");
        verify(paymentService).recalculateContractFinancials(contract);
        verify(invoiceService).syncInvoiceForContract(contract);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void extendContract_rejectsWhenVehicleUnavailableForExtendedDates() {
        Contract contract = Contract.builder()
                .id(96L).contractNumber("CTR-2026-00096").tenant(tenant)
                .status(ContractStatus.ACTIVE)
                .vehicle(vehicle)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(6))
                .rentalDays(5)
                .dailyPrice(new BigDecimal("1000.00"))
                .totalPrice(new BigDecimal("5000.00"))
                .build();

        when(contractRepository.findByIdAndTenantId(96L, 1L)).thenReturn(Optional.of(contract));
        when(availabilityService.isVehicleAvailable(eq(vehicle.getId()), any(), any(), any(), any(), any(), eq(96L)))
                .thenReturn(false);

        com.carrental.dto.contract.ExtendContractRequest request = new com.carrental.dto.contract.ExtendContractRequest();
        request.setAdditionalDays(2);

        assertThatThrownBy(() -> contractService.extendContract(96L, request))
                .isInstanceOf(com.carrental.exception.VehicleConflictException.class);

        assertThat(contract.getTotalPrice()).isEqualByComparingTo("5000.00");
        verify(contractRepository, never()).save(any());
    }

    @Test
    void extendContract_rejectsCancelledContract() {
        Contract contract = Contract.builder()
                .id(97L).contractNumber("CTR-2026-00097").tenant(tenant)
                .status(ContractStatus.CANCELLED)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now())
                .build();

        when(contractRepository.findByIdAndTenantId(97L, 1L)).thenReturn(Optional.of(contract));

        com.carrental.dto.contract.ExtendContractRequest request = new com.carrental.dto.contract.ExtendContractRequest();
        request.setAdditionalDays(2);

        assertThatThrownBy(() -> contractService.extendContract(97L, request))
                .isInstanceOf(IllegalStateException.class);
    }
}
