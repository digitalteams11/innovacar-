package com.carrental.repository;

import com.carrental.entity.Reservation;
import com.carrental.entity.ReservationStatus;
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

    /** All non-deleted reservations belonging to a tenant. */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId AND (r.deleted IS NULL OR r.deleted = false)")
    List<Reservation> findAllByTenantId(@Param("tenantId") Long tenantId);

    /** All reservations including deleted — for trash view and admin queries. */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId AND r.deleted = true")
    List<Reservation> findAllDeletedByTenantId(@Param("tenantId") Long tenantId);

    /** Find non-deleted reservations for a tenant that overlap with a specific date range. */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId " +
           "AND (r.deleted IS NULL OR r.deleted = false) " +
           "AND r.dateStart <= :endDate AND r.dateEnd >= :startDate")
    List<Reservation> findOverlappingByTenantAndDates(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Check if a specific vehicle has overlapping non-deleted reservations in the given dates. */
    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.vehicle.id = :vehicleId " +
           "AND r.tenant.id = :tenantId " +
           "AND (r.deleted IS NULL OR r.deleted = false) " +
           "AND r.status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED', 'CONVERTED_TO_CONTRACT') " +
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
           "AND (r.deleted IS NULL OR r.deleted = false) " +
           "AND r.status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED', 'CONVERTED_TO_CONTRACT') " +
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

    /** Find overlapping non-deleted reservations excluding a specific reservation. */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId " +
           "AND (:excludeId IS NULL OR r.id != :excludeId) " +
           "AND (r.deleted IS NULL OR r.deleted = false) " +
           "AND r.status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED', 'CONVERTED_TO_CONTRACT') " +
           "AND (r.dateStart < :endDate OR (r.dateStart = :endDate AND r.startTime < :endTime)) " +
           "AND (r.dateEnd > :startDate OR (r.dateEnd = :startDate AND r.endTime > :startTime))")
    List<Reservation> findOverlappingReservations(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("startTime") LocalTime startTime,
            @Param("endDate") LocalDate endDate,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId);

    /** Find overlapping non-deleted reservations for a specific vehicle. */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId " +
           "AND r.vehicle.id = :vehicleId " +
           "AND (:excludeId IS NULL OR r.id != :excludeId) " +
           "AND (r.deleted IS NULL OR r.deleted = false) " +
           "AND r.status NOT IN ('CANCELLED', 'EXPIRED', 'COMPLETED', 'CONVERTED_TO_CONTRACT') " +
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

    boolean existsByVehicleIdAndTenantIdAndStatusIn(
            Long vehicleId,
            Long tenantId,
            List<com.carrental.entity.ReservationStatus> statuses);

    /** Existence probe used to block vehicle purge — any reservation, any status, any tenant. */
    boolean existsByVehicleId(Long vehicleId);

    /** Tenant-scoped lookup by id. */
    Optional<Reservation> findByIdAndTenantId(Long id, Long tenantId);

    /** All reservations for a specific client within a tenant. */
    List<Reservation> findAllByTenantIdAndClientId(Long tenantId, Long clientId);

    long countByTenantId(Long tenantId);

    /** Deletes every reservation for a tenant — used by the Super Admin data-reset execute. */
    void deleteAllByTenantId(Long tenantId);

    /**
     * Finds an existing non-cancelled reservation for the same tenant, client,
     * vehicle, and exact date range — used by direct contract creation to stay
     * idempotent instead of creating a duplicate reservation/contract or
     * failing with a false conflict on retry/double-submit.
     */
    Optional<Reservation> findFirstByTenantIdAndClientIdAndVehicleIdAndDateStartAndDateEndAndStatusNot(
            Long tenantId, Long clientId, Long vehicleId, LocalDate dateStart, LocalDate dateEnd,
            com.carrental.entity.ReservationStatus status);

    /**
     * Non-deleted reservations in one of {@code statuses} whose end (date + time)
     * has already passed relative to {@code today}/{@code time} — used by
     * {@code VehicleStatusSyncService} to expire stale reservations that would
     * otherwise keep a vehicle stuck RESERVED forever.
     */
    @Query("SELECT r FROM Reservation r WHERE r.tenant.id = :tenantId " +
           "AND (r.deleted IS NULL OR r.deleted = false) " +
           "AND r.status IN :statuses " +
           "AND (r.dateEnd < :today OR (r.dateEnd = :today AND r.endTime < :time))")
    List<Reservation> findExpiredBlockingReservations(
            @Param("tenantId") Long tenantId,
            @Param("statuses") List<ReservationStatus> statuses,
            @Param("today") LocalDate today,
            @Param("time") LocalTime time);

    /**
     * Whether a vehicle currently has a non-expired reservation in one of
     * {@code statuses} — i.e. its end (date + time) has not yet passed.
     * Used to decide whether a vehicle is still blocked by a reservation.
     */
    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.vehicle.id = :vehicleId " +
           "AND r.tenant.id = :tenantId " +
           "AND (r.deleted IS NULL OR r.deleted = false) " +
           "AND r.status IN :statuses " +
           "AND (r.dateEnd > :today OR (r.dateEnd = :today AND r.endTime >= :time))")
    boolean existsActiveBlockingReservation(
            @Param("vehicleId") Long vehicleId,
            @Param("tenantId") Long tenantId,
            @Param("statuses") List<ReservationStatus> statuses,
            @Param("today") LocalDate today,
            @Param("time") LocalTime time);
}
