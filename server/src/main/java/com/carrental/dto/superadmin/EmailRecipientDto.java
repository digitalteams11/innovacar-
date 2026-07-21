package com.carrental.dto.superadmin;

import lombok.Builder;
import lombok.Data;

/**
 * One entry in the Super Admin Email Center's recipient directory search
 * (agency or user). Deliberately excludes anything sensitive — no password
 * hash, tokens, phone (beyond what's needed to render), or internal-only
 * fields; this DTO is what an authorized Super Admin sees while composing a
 * test send, nothing more.
 */
@Data
@Builder
public class EmailRecipientDto {
    private Long id;
    /** AGENCY | USER */
    private String type;
    private String displayName;
    private String email;
    /** Only set for type=USER — the Role enum name (OWNER/EMPLOYEE/etc.), or null for type=AGENCY. */
    private String role;
    private Long agencyId;
    private String agencyName;
    /** Tenant/account status string (ACTIVE, SUSPENDED, BLOCKED, TRIAL, ...). */
    private String status;
    private boolean verified;
    /** Only meaningful for type=AGENCY — the tenant's current plan name. */
    private String plan;
}
