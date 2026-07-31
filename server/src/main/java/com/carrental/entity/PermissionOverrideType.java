package com.carrental.entity;

/** GRANT adds a permission the user's role wouldn't otherwise have; DENY removes one the role would otherwise grant. */
public enum PermissionOverrideType {
    GRANT,
    DENY
}
