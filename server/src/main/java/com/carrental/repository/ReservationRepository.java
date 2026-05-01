package com.carrental.repository;

import com.carrental.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
           "AND r.dateStart <= :endDate AND r.dateEnd >= :startDate")
    boolean existsOverlappingReservation(
            @Param("vehicleId") Long vehicleId,
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Tenant-scoped lookup by id. */
    Optional<Reservation> findByIdAndTenantId(Long id, Long tenantId);
}
