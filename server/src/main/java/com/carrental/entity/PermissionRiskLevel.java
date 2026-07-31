package com.carrental.entity;

/** How dangerous a permission is if misassigned — drives UI risk badges and safe-default policy for new permissions. */
public enum PermissionRiskLevel {
    NORMAL,
    ELEVATED,
    DANGEROUS
}
