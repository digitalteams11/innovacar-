package com.carrental.repository;

import com.carrental.entity.Role;
import com.carrental.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Login lookup — unique per (email, tenant) pair. */
    Optional<User> findByEmailAndTenantId(String email, Long tenantId);

    /** Standard Spring Security lookup (tenantId resolved from JWT). */
    Optional<User> findByEmail(String email);

    Optional<User> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    /** All users belonging to a tenant — used by admin list endpoint. */
    List<User> findAllByTenantId(Long tenantId);

    /** All users of a given role within a tenant. */
    List<User> findAllByTenantIdAndRole(Long tenantId, Role role);

    boolean existsByEmailAndTenantId(String email, Long tenantId);

    /** Tenant-scoped existence check by id — prevents cross-tenant access. */
    boolean existsByIdAndTenantId(Long id, Long tenantId);

    /** Tenant-scoped findById — prevents cross-tenant data leak. */
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    /** Lookup by Google OAuth ID. */
    Optional<User> findByGoogleId(String googleId);

    /** Check if Google ID exists. */
    boolean existsByGoogleId(String googleId);

    /** Lookup by phone number. */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /** Check if phone number exists. */
    boolean existsByPhoneNumber(String phoneNumber);

    /** All Innovax platform staff accounts (Super Admin control center). */
    List<User> findAllByRole(Role role);

    /** Existence-only check — never loads the row, let alone the whole table. */
    boolean existsByRole(Role role);

    /** Active staff currently holding a given platform sub-role — used to guard the last SUPER_OWNER. */
    long countByRoleAndSuperAdminRole_CodeAndAccountEnabledTrue(Role role, String superAdminRoleCode);

    /**
     * Super Admin email-recipient directory search — matches email, first/last
     * name (individually and concatenated), or the owning tenant's name, so a
     * search for "atlas" finds both the agency and its staff. Server-side
     * paginated so the frontend selector never has to load the whole user
     * table. {@code :q} is expected already lowercased/trimmed by the caller.
     */
    @Query("""
            SELECT u FROM User u LEFT JOIN FETCH u.tenant t
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(u.email) LIKE CONCAT('%', :q, '%')
                OR LOWER(COALESCE(u.firstName, '')) LIKE CONCAT('%', :q, '%')
                OR LOWER(COALESCE(u.lastName, '')) LIKE CONCAT('%', :q, '%')
                OR LOWER(CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, ''))) LIKE CONCAT('%', :q, '%')
                OR LOWER(COALESCE(t.name, '')) LIKE CONCAT('%', :q, '%'))
            ORDER BY u.email ASC
            """)
    Page<User> searchForEmailRecipients(@Param("q") String q, Pageable pageable);
}
