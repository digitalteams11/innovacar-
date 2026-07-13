package com.carrental.service;

import com.carrental.dto.contract.ContractResponse;
import com.carrental.dto.contract.CreateContractRequest;
import com.carrental.entity.Client;
import com.carrental.entity.Contract;
import com.carrental.entity.ContractStatus;
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
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock private PdfService pdfService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private DepositService depositService;
    @Mock private AvailabilityService availabilityService;

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
}
