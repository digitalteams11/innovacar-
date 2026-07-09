package com.carrental.repository;

import com.carrental.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    // NOTE: Client carries @SQLRestriction("coalesce(deleted, false) = false")
    // at the entity level, so EVERY query below — regardless of whether
    // "DeletedFalse" appears in its name — already excludes soft-deleted rows.
    // The plain (non-suffixed) method names exist because several other
    // modules (contracts, reservations, payments, invoices, dashboard) call
    // them; they are kept here, with identical filtering semantics, purely to
    // preserve that existing call surface.

    /** All clients belonging to a tenant. */
    List<Client> findAllByTenantId(Long tenantId);
    List<Client> findAllByTenantIdAndDeletedFalse(Long tenantId);

    /** Tenant-scoped lookup by id — prevents cross-tenant access. */
    Optional<Client> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Client> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    /** Existence check to guard delete / update. */
    boolean existsByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantId(Long tenantId);
    long countByTenantIdAndDeletedFalse(Long tenantId);

    boolean existsByTenantIdAndPhoneIgnoreCaseAndDeletedFalse(Long tenantId, String phone);
    boolean existsByTenantIdAndEmailIgnoreCaseAndDeletedFalse(Long tenantId, String email);
    boolean existsByTenantIdAndCinIgnoreCaseAndDeletedFalse(Long tenantId, String cin);
    boolean existsByTenantIdAndPassportNumberIgnoreCaseAndDeletedFalse(Long tenantId, String passportNumber);

    Optional<Client> findFirstByTenantIdAndPhoneIgnoreCase(Long tenantId, String phone);
    Optional<Client> findFirstByTenantIdAndEmailIgnoreCase(Long tenantId, String email);
    Optional<Client> findFirstByTenantIdAndCinIgnoreCase(Long tenantId, String cin);
    Optional<Client> findFirstByTenantIdAndPassportNumberIgnoreCase(Long tenantId, String passportNumber);

    Optional<Client> findFirstByTenantIdAndPhoneIgnoreCaseAndDeletedFalse(Long tenantId, String phone);
    Optional<Client> findFirstByTenantIdAndEmailIgnoreCaseAndDeletedFalse(Long tenantId, String email);
    Optional<Client> findFirstByTenantIdAndCinIgnoreCaseAndDeletedFalse(Long tenantId, String cin);
    Optional<Client> findFirstByTenantIdAndPassportNumberIgnoreCaseAndDeletedFalse(Long tenantId, String passportNumber);
    Optional<Client> findFirstByTenantIdAndDrivingLicenseIgnoreCaseAndDeletedFalse(Long tenantId, String drivingLicense);

    /** Deleted (archived) clients only — for Task 8's admin "view deleted" endpoint. */
    @org.springframework.data.jpa.repository.Query(
            value = "select * from clients where tenant_id = :tenantId and coalesce(deleted, false) = true",
            nativeQuery = true)
    List<Client> findAllDeletedByTenantId(@Param("tenantId") Long tenantId);

    @org.springframework.data.jpa.repository.Query(
            value = "select * from clients where id = :id and tenant_id = :tenantId and coalesce(deleted, false) = true",
            nativeQuery = true)
    Optional<Client> findDeletedByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update clients
            set phone = case when phone is not null and phone <> '' then concat('deleted_', id, '_phone') else phone end,
                email = case when email is not null and email <> '' then concat('deleted_', id, '@deleted.local') else email end,
                cin = case when cin is not null and cin <> '' then concat('deleted_', id, '_cin') else cin end,
                passport_number = case when passport_number is not null and passport_number <> '' then concat('deleted_', id, '_passport') else passport_number end
            where tenant_id = :tenantId
              and coalesce(deleted, false) = true
              and (
                    (:phone is not null and lower(phone) = lower(:phone))
                 or (:email is not null and lower(email) = lower(:email))
                 or (:cin is not null and lower(cin) = lower(:cin))
                 or (:passportNumber is not null and lower(passport_number) = lower(:passportNumber))
              )
            """, nativeQuery = true)
    int releaseDeletedDuplicateIdentityFields(
            @Param("tenantId") Long tenantId,
            @Param("phone") String phone,
            @Param("email") String email,
            @Param("cin") String cin,
            @Param("passportNumber") String passportNumber);
}
