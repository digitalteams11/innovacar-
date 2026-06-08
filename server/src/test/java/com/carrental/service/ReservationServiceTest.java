package com.carrental.service;

import com.carrental.dto.reservation.CreateReservationRequest;
import com.carrental.dto.reservation.ReservationResponse;
import com.carrental.entity.Payment;
import com.carrental.entity.Reservation;
import com.carrental.entity.Tenant;
import com.carrental.entity.Vehicle;
import com.carrental.entity.VehicleStatus;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.ClientRepository;
import com.carrental.repository.ContractRepository;
import com.carrental.repository.PaymentRepository;
import com.carrental.repository.ReservationRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private DepositService depositService;
    @Mock private ContractService contractService;

    @InjectMocks
    private ReservationService reservationService;

    private static final Long TENANT_ID = 1L;
    private static final Long VEHICLE_ID = 10L;

    private Tenant tenant;
    private Vehicle vehicle;
    private Reservation existingReservation;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(TENANT_ID);

        tenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Acme Rentals")
                .build();

        vehicle = Vehicle.builder()
                .id(VEHICLE_ID)
                .marque("Audi A4")
                .prixJour(new BigDecimal("100.00"))
                .statut(VehicleStatus.AVAILABLE)
                .tenant(tenant)
                .build();

        existingReservation = Reservation.builder()
                .id(100L)
                .vehicle(vehicle)
                .dateStart(LocalDate.now().plusDays(1))
                .dateEnd(LocalDate.now().plusDays(3))
                .totalPrice(new BigDecimal("300.00"))
                .tenant(tenant)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getReservations_noFilters_returnsAll() {
        when(reservationRepository.findAllByTenantId(TENANT_ID))
                .thenReturn(List.of(existingReservation));

        List<ReservationResponse> res = reservationService.getReservations(null, null);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).getId()).isEqualTo(100L);
    }

    @Test
    void getReservations_withDates_callsOverlappingQuery() {
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().plusDays(5);

        when(reservationRepository.findOverlappingByTenantAndDates(TENANT_ID, start, end))
                .thenReturn(List.of(existingReservation));

        List<ReservationResponse> res = reservationService.getReservations(start, end);

        assertThat(res).hasSize(1);
        verify(reservationRepository).findOverlappingByTenantAndDates(TENANT_ID, start, end);
    }

    @Test
    void createReservation_success() {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setVehicleId(VEHICLE_ID);
        req.setDateStart(LocalDate.now().plusDays(10));
        req.setDateEnd(LocalDate.now().plusDays(12)); // 3 days

        when(vehicleRepository.findByIdAndTenantIdForUpdate(VEHICLE_ID, TENANT_ID))
                .thenReturn(Optional.of(vehicle));
        when(reservationRepository.existsOverlappingReservation(
                VEHICLE_ID, TENANT_ID, req.getDateStart(), LocalTime.of(9, 0),
                req.getDateEnd(), LocalTime.of(18, 0))).thenReturn(false);

        Reservation saved = Reservation.builder()
                .id(200L)
                .vehicle(vehicle)
                .dateStart(req.getDateStart())
                .dateEnd(req.getDateEnd())
                .totalPrice(new BigDecimal("300.00"))
                .tenant(tenant)
                .build();

        when(reservationRepository.save(any(Reservation.class))).thenReturn(saved);

        ReservationResponse response = reservationService.createReservation(req);

        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getTotalPrice()).isEqualByComparingTo("300.00");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createReservation_failsIfOverlapping() {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setVehicleId(VEHICLE_ID);
        req.setDateStart(LocalDate.now().plusDays(1));
        req.setDateEnd(LocalDate.now().plusDays(3));

        when(vehicleRepository.findByIdAndTenantIdForUpdate(VEHICLE_ID, TENANT_ID))
                .thenReturn(Optional.of(vehicle));
        when(reservationRepository.existsOverlappingReservation(
                VEHICLE_ID, TENANT_ID, req.getDateStart(), LocalTime.of(9, 0),
                req.getDateEnd(), LocalTime.of(18, 0))).thenReturn(true);

        assertThatThrownBy(() -> reservationService.createReservation(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void createReservation_failsIfStartAfterEnd() {
        CreateReservationRequest req = new CreateReservationRequest();
        req.setVehicleId(VEHICLE_ID);
        req.setDateStart(LocalDate.now().plusDays(5));
        req.setDateEnd(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> reservationService.createReservation(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start date cannot be after end date");
    }

    @Test
    void deleteReservation_success() {
        when(reservationRepository.findByIdAndTenantId(100L, TENANT_ID))
                .thenReturn(Optional.of(existingReservation));

        reservationService.deleteReservation(100L);

        verify(reservationRepository).save(existingReservation);
        assertThat(existingReservation.getStatus()).isEqualTo(com.carrental.entity.ReservationStatus.CANCELLED);
    }
}
