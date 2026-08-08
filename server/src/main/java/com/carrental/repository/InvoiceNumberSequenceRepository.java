package com.carrental.repository;

import com.carrental.entity.InvoiceNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceNumberSequenceRepository
        extends JpaRepository<InvoiceNumberSequence, InvoiceNumberSequence.Key> {

    /**
     * Creates the (tenant, year) counter row if it doesn't exist yet.
     * {@code ON CONFLICT DO NOTHING} is atomic and never throws on a
     * concurrent duplicate insert, unlike a plain INSERT — deliberately
     * avoiding a caught {@code DataIntegrityViolationException}, which in
     * Postgres would leave the surrounding transaction unusable for any
     * further statement (aborted-transaction state) instead of just failing
     * this one insert.
     */
    @Modifying
    @Query(value = "INSERT INTO invoice_number_sequences (tenant_id, year, last_number) "
            + "VALUES (:tenantId, :year, 0) ON CONFLICT (tenant_id, year) DO NOTHING", nativeQuery = true)
    void ensureRowExists(@Param("tenantId") Long tenantId, @Param("year") Integer year);

    /**
     * Row-locks the (tenant, year) counter for the duration of the caller's
     * transaction — the lock, not the {@code count()+1} read, is what makes
     * concurrent invoice creation safe (see NumberGeneratorService). Call
     * {@link #ensureRowExists} first so the row is guaranteed to exist.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceNumberSequence s WHERE s.tenantId = :tenantId AND s.year = :year")
    Optional<InvoiceNumberSequence> lockForUpdate(@Param("tenantId") Long tenantId, @Param("year") Integer year);
}
