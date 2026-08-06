package com.carrental.dto.superadmin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Real-database-count summary of what a permanent agency delete would
 * touch, backing {@code GET /api/super-admin/agencies/{id}/deletion-impact}.
 * {@code canDeleteImmediately} is only ever true when every dependent count
 * is zero and there is no active subscription — a populated agency must
 * always go through archive instead (spec: "do not use unsafe hard delete
 * by default").
 */
@Data
@Builder
public class AgencyDeletionImpactDto {
    private Long agencyId;
    private String agencyName;
    private long users;
    private long vehicles;
    private long clients;
    private long reservations;
    private long contracts;
    private long activeContracts;
    private long payments;
    private long invoices;
    private long documents;
    private boolean activeSubscription;
    private boolean protectedAgency;
    private boolean canDeleteImmediately;
    private List<BlockingReason> blockingReasons;

    @Data
    @Builder
    public static class BlockingReason {
        private String code;
        private String message;
    }
}
