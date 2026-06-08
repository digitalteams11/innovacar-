package com.carrental.repository;

import com.carrental.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** All reservations belonging to a tenant. */
    List<Reservation> findAllByTenantId(Long tenantId);

    /** Find reservations for a tenant that overlap with a specific date range. */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId " +
           "AND r.dateStart <= :endDate AND r.dateEnd >= :startDate")
    List<Reservation> findOverlappingByTenantAndDates(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Check if a specific vehicle has overlapping reservations in the given dates. */
    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.vehicle.id = :vehicleId " +
           "AND r.tenant.id = :tenantId " +
           "AND r.status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED') " +
           "AND (r.dateStart < :endDate OR (r.dateStart = :endDate AND r.startTime < :endTime)) " +
           "AND (r.dateEnd > :startDate OR (r.dateEnd = :startDate AND r.endTime > :startTime))")
    boolean existsOverlappingReservation(
            @Param("vehicleId") Long vehicleId,
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime);

    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.vehicle.id = :vehicleId " +
           "AND r.tenant.id = :tenantId AND r.id != :reservationId " +
           "AND r.status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED') " +
           "AND (r.dateStart < :endDate OR (r.dateStart = :endDate AND r.startTime < :endTime)) " +
           "AND (r.dateEnd > :startDate OR (r.dateEnd = :startDate AND r.endTime > :startTime))")
    boolean existsOverlappingReservationExcluding(
            @Param("reservationId") Long reservationId,
            @Param("vehicleId") Long vehicleId,
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime);

    /** Find overlapping reservations excluding a specific reservation. */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId " +
           "AND (:excludeId IS NULL OR r.id != :excludeId) " +
           "AND r.status NOT IN ('CANCELLED', 'EXPIRED') " +
           "AND (r.dateStart < :endDate OR (r.dateStart = :endDate AND r.startTime < :endTime)) " +
           "AND (r.dateEnd > :startDate OR (r.dateEnd = :startDate AND r.endTime > :startTime))")
    List<Reservation> findOverlappingReservations(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId);

    /** Find overlapping reservations for a specific vehicle. */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId " +
           "AND r.vehicle.id = :vehicleId " +
           "AND (:excludeId IS NULL OR r.id != :excludeId) " +
           "AND r.status NOT IN ('CANCELLED', 'EXPIRED') " +
           "AND (r.dateStart < :endDate OR (r.dateStart = :endDate AND r.startTime < :endTime)) " +
           "AND (r.dateEnd > :startDate OR (r.dateEnd = :startDate AND r.endTime > :startTime))")
    List<Reservation> findOverlappingReservationsForVehicle(
            @Param("tenantId") Long tenantId,
            @Param("vehicleId") Long vehicleId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId);

    /** Find reservations by vehicle and tenant, excluding cancelled. */
    List<Reservation> findByVehicleIdAndTenantIdAndStatusNot(Long vehicleId, Long tenantId, com.carrental.entity.ReservationStatus status);

    boolean existsByVehicleIdAndTenantIdAndIdNotAndStatusIn(
            Long vehicleId,
            Long tenantId,
            Long id,
            List<com.carrental.entity.ReservationStatus> statuses);

    /** Tenant-scoped lookup by id. */
    Optional<Reservation> findByIdAndTenantId(Long id, Long tenantId);

    /** All reservations for a specific client within a tenant. */
    List<Reservation> findAllByTenantIdAndClientId(Long tenantId, Long clientId);
}
